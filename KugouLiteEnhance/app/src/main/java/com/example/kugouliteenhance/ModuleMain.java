package com.example.kugouliteenhance;

import android.content.res.XModuleResources;

import com.example.kugouliteenhance.config.HookConfig;
import com.example.kugouliteenhance.hook.HookManager;
import com.example.kugouliteenhance.util.XposedLog;

import org.luckypray.dexkit.DexKitBridge;

import java.io.InputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Module entry point. Implements the standard Xposed lifecycle:
 *  - initZygote: capture module path and resolve bundled resources
 *  - handleLoadPackage: load config, build DexKit bridge, apply hooks to target
 */
public final class ModuleMain implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static String modulePath;
    private static XModuleResources moduleRes;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable t) {
            XposedLog.w("DexKit native lib not loadable in this process: " + t);
        }
        try {
            moduleRes = XModuleResources.createInstance(modulePath, null);
        } catch (Throwable t) {
            XposedLog.e("Failed to create module resources", t);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!isTarget(lpparam.packageName)) return;
        XposedLog.i("Target loaded: " + lpparam.packageName + " (" + lpparam.processName + ")");

        try {
            HookConfig config = loadConfig();
            DexKitBridge bridge = null;
            try {
                bridge = DexKitBridge.create(lpparam.classLoader);
            } catch (Throwable t) {
                XposedLog.w("DexKit bridge unavailable; feature discovery degraded: " + t);
            }

            new HookManager(config, lpparam.classLoader, bridge).applyAll();
        } catch (Throwable t) {
            XposedLog.e("Fatal error applying hooks", t);
        }
    }

    private boolean isTarget(String pkg) {
        if (pkg == null) return false;
        // Target packages are defined in hook_config.json; also accept the
        // default package so the module works before config is parsed.
        return "com.kugou.android.lite".equals(pkg);
    }

    private HookConfig loadConfig() throws Exception {
        InputStream in = moduleRes != null
                ? moduleRes.getAssets().open("hook_config.json")
                : ModuleMain.class.getClassLoader().getResourceAsStream("hook_config.json");
        if (in == null) throw new IllegalStateException("hook_config.json not found");
        try {
            return HookConfig.parse(in);
        } finally {
            in.close();
        }
    }
}
