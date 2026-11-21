package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Ported from Kugou Concept 2.5.5 "watch-ad-to-claim-VIP" flow.
 *
 * What this hook actually does (be honest about the limit):
 *  1. Auto-reward: hooks the GDT reward listener (located by the stable toast
 *     literal "激励领取成功~" and the onADClose method) and force-sets its
 *     boolean reward flag so the onGetReward callback fires without watching
 *     the full 15s ad.
 *  2. Report normalization: hooks AdPlayReportProtocol.report(String,long,long)
 *     (located by method name + the "play_report" URL literal) and rewrites
 *     play_start / play_end to a valid >= GAIN_REWARD_TIME window before the
 *     POST is sent.
 *
 * LIMIT: VIP entitlement is granted by the SERVER from the play_report
 * response (award_vip_hour). This hook automates/accelerates the client flow
 * but cannot make the server grant VIP if ad_id/replay checks reject it.
 * See VIP_2.5.5_ANALYSIS.md section 6.
 */
public final class AutoClaimVipHook extends BaseHook {

    private static final String REWARD_TOAST = "激励领取成功~";
    private static final long DEFAULT_MIN_DURATION_MS = 20000L; // > GAIN_REWARD_TIME(15s)

    private final DexKitBridge bridge;
    private final ClassLoader classLoader;
    private final long minDurationMs;

    public AutoClaimVipHook(HookRule rule, DexKitBridge bridge, ClassLoader classLoader) {
        super(rule);
        this.bridge = bridge;
        this.classLoader = classLoader;
        this.minDurationMs = parseMinDuration(rule);
    }

    private static long parseMinDuration(HookRule rule) {
        try {
            if (rule.value instanceof JSONObject) {
                return ((JSONObject) rule.value).optLong("minDurationMs", DEFAULT_MIN_DURATION_MS);
            }
            if (rule.value instanceof Number) {
                return ((Number) rule.value).longValue();
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_MIN_DURATION_MS;
    }

    @Override
    public void apply() {
        Set<Method> reportMethods = findReportMethods();
        Set<Method> rewardMethods = findRewardMethods();
        Set<Method> closeMethods = findAdCloseMethods();
        XposedLog.i("AutoClaimVip: found " + reportMethods.size() + " report, "
                + rewardMethods.size() + " reward, " + closeMethods.size() + " onADClose methods");

        for (Method m : reportMethods) hookReport(m);
        for (Method m : rewardMethods) forceFlagHook(m);
        for (Method m : closeMethods) forceFlagHook(m);
    }

    /** report(String,long,long): name + URL literal, ANDed inside one method. */
    private Set<Method> findReportMethods() {
        Set<Method> out = new HashSet<>();
        String[] names = nonEmpty(rule.methodName);
        String[] strings = nonEmpty(rule.stringConstants);
        if (names.length == 0) names = new String[]{"a", "report"};
        for (String name : names) {
            for (String s : strings) {
                MethodMatcher mm = new MethodMatcher();
                mm.name(name);
                mm.addUsingString(s, StringMatchType.Contains, false);
                collect(out, mm);
            }
        }
        return out;
    }

    /** GDT listener onReward(): located by the stable reward toast literal. */
    private Set<Method> findRewardMethods() {
        Set<Method> out = new HashSet<>();
        MethodMatcher mm = new MethodMatcher();
        mm.addUsingString(REWARD_TOAST, StringMatchType.Contains, false);
        collect(out, mm);
        return out;
    }

    /** GDT listener onADClose(): located by method name + class anchors. */
    private Set<Method> findAdCloseMethods() {
        Set<Method> out = new HashSet<>();
        String[] classes = nonEmpty(rule.classNameContains);
        for (String cls : classes) {
            MethodMatcher mm = new MethodMatcher();
            mm.name("onADClose");
            mm.declaredClass(cls.replace('.', '/'), StringMatchType.Contains, false);
            collect(out, mm);
        }
        // Fallback: onADClose co-occurring with the toast anchor.
        MethodMatcher alt = new MethodMatcher();
        alt.name("onADClose");
        alt.addUsingString(REWARD_TOAST, StringMatchType.Contains, false);
        collect(out, alt);
        return out;
    }

    private void collect(Set<Method> out, MethodMatcher matcher) {
        try {
            for (MethodData data : bridge.findMethod(FindMethod.create().matcher(matcher))) {
                try {
                    Method m = data.getMethodInstance(classLoader);
                    if (m != null) out.add(m);
                } catch (Throwable ignored) {
                    // Hidden API / unresolvable class — skip.
                }
            }
        } catch (Throwable t) {
            XposedLog.e("AutoClaimVip: DexKit query failed", t);
        }
    }

    private void hookReport(final Method m) {
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] args = param.args;
                    if (args != null && args.length >= 3
                            && args[1] instanceof Long && args[2] instanceof Long) {
                        long now = System.currentTimeMillis();
                        args[1] = now - minDurationMs;
                        args[2] = now;
                        XposedLog.i("AutoClaimVip: normalized play window to " + minDurationMs + "ms in "
                                + m.getDeclaringClass().getName() + "." + m.getName());
                    }
                }
            });
            XposedLog.d("AutoClaimVip: report hook applied -> " + m.getName());
        } catch (Throwable t) {
            XposedLog.e("AutoClaimVip: report hook failed on " + m.getName(), t);
        }
    }

    private void forceFlagHook(Method m) {
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    forceRewardFlag(param.thisObject);
                }
            });
            XposedLog.d("AutoClaimVip: reward-flag hook applied -> " + m.getDeclaringClass().getName() + "." + m.getName());
        } catch (Throwable t) {
            XposedLog.e("AutoClaimVip: reward-flag hook failed on " + m.getName(), t);
        }
    }

    /** Best-effort: set every boolean field on the GDT listener to true so the
     *  reward path is treated as complete. Scoped to the listener object only. */
    private void forceRewardFlag(Object target) {
        if (target == null) return;
        try {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        if (!f.getBoolean(target)) {
                            f.setBoolean(target, true);
                            XposedLog.d("AutoClaimVip: forced flag " + c.getName() + "." + f.getName());
                        }
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
            XposedLog.e("AutoClaimVip: forceRewardFlag failed", t);
        }
    }

    private static String[] nonEmpty(String[] arr) {
        if (arr == null) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : arr) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }
}
