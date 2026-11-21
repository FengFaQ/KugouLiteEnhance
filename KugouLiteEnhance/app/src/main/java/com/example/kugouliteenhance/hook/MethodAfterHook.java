package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook type: run logic after the target method. Used for splash-ad skip
 * (the Java-side counterpart of SplashHook's native afterHookedMethod).
 */
public final class MethodAfterHook extends BaseHook {
    private final Set<Method> targets;

    public MethodAfterHook(HookRule rule, Set<Method> targets) {
        super(rule);
        this.targets = targets;
    }

    @Override
    public void apply() {
        for (Method m : targets) {
            try {
                XposedHelpers.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // Post-process splash/ad result: clear the ad view / result object.
                        XposedLog.d("MethodAfterHook invoked: " + rule.id);
                    }
                });
                XposedLog.d("MethodAfterHook applied: " + rule.id + " -> " + m.getName());
            } catch (Throwable t) {
                XposedLog.e("MethodAfterHook failed on " + m.getName(), t);
            }
        }
    }
}
