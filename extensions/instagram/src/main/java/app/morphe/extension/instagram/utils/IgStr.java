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

    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private IgStr() {}

    public static String str(String name) {
        String cached = cache.get(name);
        if (cached != null) return cached;
        try {
            Context ctx = baseContext();
            int id = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
            if (id == 0) return name;
            String value = ctx.getString(id);
            cache.put(name, value);
            return value;
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
        Context ctx = Utils.getActivity();
        if (ctx == null) ctx = Utils.getContext();
        while (ctx instanceof ContextWrapper) {
            Context base = ((ContextWrapper) ctx).getBaseContext();
            if (base == null || base == ctx) break;
            ctx = base;
        }
        return ctx;
    }
}
