package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook type: force a method to return a constant (false / null / void).
 * Counterpart of reference: J.hookReturnFalse, x.hookReturnNull,
 * P.hookReturnVoid, SignatureHook.hookBooleanCheckMethods.
 */
public final class ReturnConstantHook extends BaseHook {
    private final Set<Method> targets;

    public ReturnConstantHook(HookRule rule, Set<Method> targets) {
        super(rule);
        this.targets = targets;
    }

    @Override
    public void apply() {
        Object value = rule.hasValue ? rule.value : defaultValue(rule.returnType);
        for (Method m : targets) {
            try {
                XposedHelpers.hookMethod(m, XC_MethodReplacement.returnConstant(value));
                XposedLog.d("ReturnConstantHook applied: " + rule.id + " -> " + m.getName());
            } catch (Throwable t) {
                XposedLog.e("ReturnConstantHook failed on " + m.getName(), t);
            }
        }
    }

    private static Object defaultValue(String returnType) {
        if (returnType == null || returnType.isEmpty()) return null;
        switch (returnType) {
            case "boolean":
            case "java.lang.Boolean":
                return false;
            case "int":
                return 0;
            case "long":
                return 0L;
            case "void":
                return null;
            default:
                return null;
        }
    }
}
