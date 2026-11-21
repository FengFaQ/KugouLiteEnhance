package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;
import com.example.kugouliteenhance.util.XposedLog;

/**
 * Hook type: SO-layer signature check bypass.
 *
 * The reference sample ships libkgsignaturehook.so and registers natives via
 * SignatureHook.nativeInstall(). This independent project intentionally does
 * NOT bundle that proprietary native binary. The hook point is declared here
 * as an extension slot: to enable it, add a lawfully-obtained .so and call
 * System.loadLibrary(...) + nativeInstall() from apply().
 */
public final class NativeHook extends BaseHook {

    public NativeHook(HookRule rule) {
        super(rule);
    }

    @Override
    public void apply() {
        XposedLog.w("NativeHook (" + rule.id + ") is an extension slot. "
                + "Provide lib" + rule.nativeLibrary + ".so and register natives to enable "
                + "SO-layer signature bypass. Left disabled by default in hook_config.json.");
    }
}
