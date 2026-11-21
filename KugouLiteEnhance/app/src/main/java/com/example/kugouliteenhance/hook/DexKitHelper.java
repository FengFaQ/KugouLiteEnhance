package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime feature discovery — the independent-module counterpart of the
 * reference sample's hookByDexKit / hookByCache / preloadDexKitCache.
 *
 * Targets are matched by method name / name fragments / return type instead
 * of hard-coded obfuscated symbols, so version upgrades only need config edits.
 */
public final class DexKitHelper {

    private DexKitHelper() {}

    /**
     * Finds methods matching a rule using DexKit, then resolves them to real
     * java.lang.reflect.Method objects through the target ClassLoader.
     */
    public static Set<Method> findMethods(DexKitBridge bridge, ClassLoader cl, HookRule rule) {
        Set<Method> result = new HashSet<>();
        if (bridge == null) return result;

        MethodMatcher matcher = new MethodMatcher();
        boolean any = false;

        for (String name : rule.methodName) {
            if (!name.isEmpty()) { matcher.addName(name); any = true; }
        }
        for (String frag : rule.methodNameContains) {
            if (!frag.isEmpty()) { matcher.addName(frag); any = true; }
        }
        if (!rule.returnType.isEmpty()) {
            matcher.returnType(rule.returnType.replace('.', '/'));
            any = true;
        }
        if (!any) return result;

        try {
            List<MethodData> dataList = bridge.findMethod(FindMethod.create().matcher(matcher));
            for (MethodData data : dataList) {
                try {
                    Method m = data.getMethodInstance(cl);
                    if (m != null) result.add(m);
                } catch (Throwable ignored) {
                    // Some matches may be hidden API or otherwise unresolvable.
                }
            }
        } catch (Throwable t) {
            // DexKit query failed; caller logs at a higher level.
        }
        return result;
    }
}
