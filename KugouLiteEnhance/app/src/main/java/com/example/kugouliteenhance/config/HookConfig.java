package com.example.kugouliteenhance.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses hook_config.json into strongly typed {@link HookRule} objects.
 * Mirrors the reference module's intent of externalising targets so that
 * version-specific obfuscation changes are config edits, not code edits.
 */
public final class HookConfig {

    private final List<HookRule> rules = new ArrayList<>();
    private final List<String> targetPackages = new ArrayList<>();

    private HookConfig() {}

    public static HookConfig parse(InputStream in) throws Exception {
        HookConfig cfg = new HookConfig();
        String json = readAll(in);
        JSONObject root = new JSONObject(json);

        JSONArray pkgs = root.optJSONArray("targetPackages");
        if (pkgs != null) {
            for (int i = 0; i < pkgs.length(); i++) {
                cfg.targetPackages.add(pkgs.getString(i));
            }
        }

        JSONArray hooks = root.optJSONArray("hooks");
        if (hooks != null) {
            for (int i = 0; i < hooks.length(); i++) {
                JSONObject o = hooks.getJSONObject(i);
                HookRule rule = new HookRule();
                rule.id = o.optString("id", "hook_" + i);
                rule.type = o.optString("type", "");
                rule.description = o.optString("description", "");
                rule.enabled = o.optBoolean("enabled", false);

                JSONObject target = o.optJSONObject("target");
                if (target != null) {
                    rule.methodName = optStringArray(target, "methodName");
                    rule.methodNameContains = optStringArray(target, "methodNameContains");
                    rule.classNameContains = optStringArray(target, "classNameContains");
                    rule.returnType = target.optString("returnType", "");
                    rule.nativeLibrary = target.optString("nativeLibrary", "");
                }
                if (o.has("value") && !o.isNull("value")) {
                    rule.value = o.get("value");
                    rule.hasValue = true;
                }
                cfg.rules.add(rule);
            }
        }
        return cfg;
    }

    public List<HookRule> getRules() {
        return rules;
    }

    public List<String> getTargetPackages() {
        return targetPackages;
    }

    private static String[] optStringArray(JSONObject o, String key) {
        JSONArray arr = o.optJSONArray(key);
        if (arr == null) return new String[0];
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) out[i] = arr.optString(i, "");
        return out;
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
