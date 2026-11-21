package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;

/**
 * Hook type: force signature-check methods to report success (JAVA layer).
 * Counterpart of reference: SignatureHook.forceLegalFlag +
 * hookBooleanCheckMethods + hookStaticVoidContextMethods.
 */
public final class FieldFlagHook extends BaseHook {
    private final Set<Method> targets;

    public FieldFlagHook(HookRule rule, Set<Method> targets) {
        super(rule);
        this.targets = targets;
    }

    @Override
    public void apply() {
        for (Method m : targets) {
            try {
                Class<?> rt = m.getReturnType();
                Object passValue = (rt == boolean.class) ? true : null;
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(passValue));
                XposedLog.d("FieldFlagHook applied: " + rule.id + " -> " + m.getName());
            } catch (Throwable t) {
                XposedLog.e("FieldFlagHook failed on " + m.getName(), t);
            }
        }
    }
}
