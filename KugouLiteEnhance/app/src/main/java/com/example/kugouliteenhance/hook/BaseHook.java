package com.example.kugouliteenhance.hook;

import com.example.kugouliteenhance.config.HookRule;

/**
 * Base abstraction for every hook type. Each concrete hook type implements
 * {@link #apply()} against its discovered target methods.
 */
public abstract class BaseHook {
    protected final HookRule rule;

    protected BaseHook(HookRule rule) {
        this.rule = rule;
    }

    public abstract void apply();

    public String id() {
        return rule.id;
    }
}
