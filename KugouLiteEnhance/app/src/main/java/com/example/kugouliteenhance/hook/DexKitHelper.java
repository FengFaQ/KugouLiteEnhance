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
 * Targets are matched by method name / name fragments, declaring class name
 * fragments and string constants instead of hard-coded obfuscated symbols, so
 * version upgrades only need config edits.
 *
 * Anchors within one category are alternatives (OR); method/class anchors and
 * string anchors are run as SEPARATE queries and the results unioned. This
 * avoids a zero-match caused by ANDing a string that does not co-occur with a
 * method inside the same dex method. Both the matcher count and the resolved
 * method count are capped so a broad anchor can never become hundreds of hooks.
 */
public final class DexKitHelper {

    private static final int MAX_MATCHERS = 32;
    private static final int MAX_METHODS_PER_RULE = 48;

    private DexKitHelper() {}

    public static Set<Method> findMethods(DexKitBridge bridge, ClassLoader cl, HookRule rule) {
        Set<Method> result = new HashSet<>();
        if (bridge == null) return result;

        String[] exactNames = nonEmpty(rule.methodName);
        String[] nameFrags = nonEmpty(rule.methodNameContains);
        String[] classes = nonEmpty(rule.classNameContains);
        String[] strings = nonEmpty(rule.stringConstants);

        List<MethodMatcher> matchers = new ArrayList<>();

        // (1) Method/class anchors. Cross them (AND) so a fragment like
        // "getBanner" resolves only inside the intended classes.
        for (String name : exactNames) {
            if (classes.length == 0) {
                addMatcher(matchers, methodMatcher(name, null, null, rule));
            } else {
                for (String cls : classes) addMatcher(matchers, methodMatcher(name, null, cls, rule));
            }
        }
        for (String frag : nameFrags) {
            if (classes.length == 0) {
                addMatcher(matchers, methodMatcher(null, frag, null, rule));
            } else {
                for (String cls : classes) addMatcher(matchers, methodMatcher(null, frag, cls, rule));
            }
        }
        if (exactNames.length == 0 && nameFrags.length == 0) {
            for (String cls : classes) addMatcher(matchers, methodMatcher(null, null, cls, rule));
        }

        // (2) String anchors, one matcher each (DexKit ANDs multiple strings,
        // but the config lists them as alternatives). When a class anchor is
        // also present, narrow the string query to that class.
        for (String s : strings) {
            if (classes.length == 0) {
                addMatcher(matchers, stringMatcher(s, null));
            } else {
                for (String cls : classes) addMatcher(matchers, stringMatcher(s, cls));
            }
        }

        if (matchers.isEmpty()) return result;

        for (MethodMatcher matcher : matchers) {
            if (result.size() >= MAX_METHODS_PER_RULE) break;
            try {
                List<MethodData> dataList = bridge.findMethod(FindMethod.create().matcher(matcher));
                for (MethodData data : dataList) {
                    if (result.size() >= MAX_METHODS_PER_RULE) break;
                    try {
                        Method m = data.getMethodInstance(cl);
                        if (m != null) result.add(m);
                    } catch (Throwable ignored) {
                        // Hidden API / unresolvable class — skip this match.
                    }
                }
            } catch (Throwable ignored) {
                // This query failed; continue with the remaining matchers.
            }
        }
        return result;
    }

    private static void addMatcher(List<MethodMatcher> matchers, MethodMatcher m) {
        if (matchers.size() < MAX_MATCHERS) matchers.add(m);
    }

    private static MethodMatcher methodMatcher(String exactName, String nameFrag, String className,
                                               HookRule rule) {
        MethodMatcher m = new MethodMatcher();
        if (exactName != null) m.name(exactName);
        if (nameFrag != null) m.name(nameFrag, StringMatchType.Contains, false);
        if (className != null) m.declaredClass(className.replace('.', '/'), StringMatchType.Contains, false);
        if (rule.returnType != null && !rule.returnType.isEmpty()) {
            m.returnType(rule.returnType.replace('.', '/'));
        }
        return m;
    }

    private static MethodMatcher stringMatcher(String stringConst, String className) {
        MethodMatcher m = new MethodMatcher();
        if (className != null) m.declaredClass(className.replace('.', '/'), StringMatchType.Contains, false);
        m.addUsingString(stringConst, StringMatchType.Contains, false);
        return m;
    }

    private static String[] nonEmpty(String[] arr) {
        if (arr == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (String s : arr) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }
}
