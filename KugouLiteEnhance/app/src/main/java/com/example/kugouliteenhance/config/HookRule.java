package com.example.kugouliteenhance.config;

/** One configured hook rule: a hook type plus a target descriptor. */
public final class HookRule {
    public String id;
    public String type;
    public String description;
    public boolean enabled;

    // Target descriptor
    public String[] methodName = new String[0];
    public String[] methodNameContains = new String[0];
    public String[] classNameContains = new String[0];
    public String returnType = "";
    public String nativeLibrary = "";

    // Optional replacement value for ReturnConstantHook
    public Object value;
    public boolean hasValue;

    @Override
    public String toString() {
        return "HookRule{" + id + " [" + type + "] enabled=" + enabled + "}";
    }
}
