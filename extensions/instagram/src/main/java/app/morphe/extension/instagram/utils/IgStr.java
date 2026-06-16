/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.utils;

import android.content.Context;
import android.content.ContextWrapper;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Utils;

public final class IgStr {

    private static final ConcurrentHashMap<String, Integer> idCache = new ConcurrentHashMap<>();
    private static Context baseContext;

    private IgStr() {}

    public static String str(String name) {
        try {
            Context ctx = baseContext();
            Integer cached = idCache.get(name);
            int id = cached != null ? cached : ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
            if (id == 0) return name;
            if (cached == null) idCache.put(name, id);
            return ctx.getString(id);
        } catch (Exception e) {
            return name;
        }
    }

    public static String str(String name, Object... args) {
        String template = str(name);
        if (template.equals(name)) return name;
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    private static Context baseContext() {
        if (baseContext != null) return baseContext;
        Context ctx = Utils.getContext();
        while (ctx instanceof ContextWrapper) {
            Context base = ((ContextWrapper) ctx).getBaseContext();
            if (base == null || base == ctx) break;
            ctx = base;
        }
        baseContext = ctx;
        return baseContext;
    }
}