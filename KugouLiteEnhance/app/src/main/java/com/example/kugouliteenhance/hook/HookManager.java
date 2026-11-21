package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookConfig;
import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import org.luckypray.dexkit.DexKitBridge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Central dispatcher — analogous to the reference MainHook.AnonymousClass1
 * which fans out to SignatureHook / SplashHook / K / J / x / P / C1042m etc.
 * Here each config rule is turned into a concrete BaseHook by type name.
 */
public final class HookManager {

    private final HookConfig config;
    private final ClassLoader classLoader;
    private final DexKitBridge bridge;

    public HookManager(HookConfig config, ClassLoader classLoader, DexKitBridge bridge) {
        this.config = config;
        this.classLoader = classLoader;
        this.bridge = bridge;
    }

    public void applyAll() {
        List<HookRule> rules = config.getRules();
        int applied = 0;
        for (HookRule rule : rules) {
            if (!rule.enabled) {
                XposedLog.d("SKIP disabled hook: " + rule.id);
                continue;
            }
            try {
                BaseHook hook = createHook(rule);
                if (hook != null) {
                    hook.apply();
                    applied++;
                }
            } catch (Throwable t) {
                XposedLog.e("Failed to apply hook " + rule.id, t);
            }
        }
        XposedLog.i("Applied " + applied + "/" + rules.size() + " hooks");
    }

    private BaseHook createHook(HookRule rule) {
        // Hooks that resolve targets through DexKit
        if ("ActivityLaunchHook".equals(rule.type)) return new ActivityLaunchHook(rule);
        if ("NativeHook".equals(rule.type)) return new NativeHook(rule);
        // Ported 2.5.5 auto-claim-VIP flow (reward flag + report window normalization)
        if ("AutoClaimVipHook".equals(rule.type)) return new AutoClaimVipHook(rule, bridge, classLoader);

        Set<Method> targets = DexKitHelper.findMethods(bridge, classLoader, rule);
        if (targets.isEmpty()) {
            XposedLog.w("No targets found for " + rule.id + " (" + rule.type + ")");
            return null;
        }

        switch (rule.type) {
            case "ReturnConstantHook":
                return new ReturnConstantHook(rule, targets);
            case "MethodReplaceHook":
                return new MethodReplaceHook(rule, targets);
            case "MethodAfterHook":
                return new MethodAfterHook(rule, targets);
            case "FieldFlagHook":
                return new FieldFlagHook(rule, targets);
            default:
                XposedLog.w("Unknown hook type: " + rule.type);
                return null;
        }
    }
}
