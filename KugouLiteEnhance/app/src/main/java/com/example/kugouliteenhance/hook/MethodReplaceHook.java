package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;

/**
 * Hook type: replace a method's logic entirely. Used for H5 intercept rewrite.
 * Counterpart of reference: x.init / t.init hook gate logic.
 */
public final class MethodReplaceHook extends BaseHook {
    private final Set<Method> targets;

    public MethodReplaceHook(HookRule rule, Set<Method> targets) {
        super(rule);
        this.targets = targets;
    }

    @Override
    public void apply() {
        for (Method m : targets) {
            try {
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        // H5 intercept: short-circuit "网络繁忙，请稍后重试" path.
                        // Default to a benign false/null result; tune per target.
                        XposedLog.d("MethodReplaceHook invoked: " + rule.id);
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) return false;
                        return null;
                    }
                });
                XposedLog.d("MethodReplaceHook applied: " + rule.id + " -> " + m.getName());
            } catch (Throwable t) {
                XposedLog.e("MethodReplaceHook failed on " + m.getName(), t);
            }
        }
    }
}
