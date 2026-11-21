package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

import android.app.Activity;
import android.content.Intent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook type: intercept Activity.startActivity* to skip guide/splash screens.
 * Counterpart of reference: C1044o.hookActivityLaunch and
 * C1045p.hookActivityLaunchers (which hook Intent and Intent+Bundle overloads).
 */
public final class ActivityLaunchHook extends BaseHook {

    public ActivityLaunchHook(HookRule rule) {
        super(rule);
    }

    @Override
    public void apply() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Intent intent = (Intent) param.args[0];
                            if (intent == null) return;
                            String name = intent.getComponent() != null
                                    ? intent.getComponent().getClassName() : "";
                            for (String frag : rule.classNameContains) {
                                if (!frag.isEmpty() && name.contains(frag)) {
                                    XposedLog.d("ActivityLaunchHook blocked: " + name);
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }
                    });
            XposedLog.d("ActivityLaunchHook applied: " + rule.id);
        } catch (Throwable t) {
            XposedLog.e("ActivityLaunchHook failed", t);
        }
    }
}
