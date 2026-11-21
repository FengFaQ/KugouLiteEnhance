package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
     *
     * DexKit's MethodMatcher holds a single name matcher, so each configured
     * name / name-fragment is queried separately and the results are unioned.
     */
    public static Set<Method> findMethods(DexKitBridge bridge, ClassLoader cl, HookRule rule) {
        Set<Method> result = new HashSet<>();
        if (bridge == null) return result;

        // Build one matcher per configured name/fragment, all sharing the
        // optional return-type constraint.
        List<MethodMatcher> matchers = new ArrayList<>();
        for (String name : rule.methodName) {
            if (name == null || name.isEmpty()) continue;
            MethodMatcher m = new MethodMatcher().name(name);
            applyReturnType(m, rule);
            matchers.add(m);
        }
        for (String frag : rule.methodNameContains) {
            if (frag == null || frag.isEmpty()) continue;
            // Contains match on the method name.
            MethodMatcher m = new MethodMatcher().name(frag, StringMatchType.Contains, false);
            applyReturnType(m, rule);
            matchers.add(m);
        }
        // Return-type-only rule (no name info): a single matcher with just the
        // return type would match everything, so skip it to stay safe.
        if (matchers.isEmpty()) return result;

        for (MethodMatcher matcher : matchers) {
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
                // This particular query failed; continue with the other matchers.
            }
        }
        return result;
    }

    private static void applyReturnType(MethodMatcher matcher, HookRule rule) {
        if (rule.returnType != null && !rule.returnType.isEmpty()) {
            // DexKit expects a class descriptor; dotted Java names use '/' separators.
            matcher.returnType(rule.returnType.replace('.', '/'));
        }
    }
}
