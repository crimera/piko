package app.morphe.extension.newx.settings;

import android.app.Activity;
import android.content.Context;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Resource references retain their identity, never the text of a previous locale. */
public final class NewXStrings extends StringRef {
    private final String resourceName;
    private final int resourceId;

    private NewXStrings(String name, int id) {
        super(name);
        resourceName = name;
        resourceId = id;
    }

    public static NewXStrings sfc(String name) {
        return new NewXStrings(name, 0);
    }

    public static NewXStrings sf(String name) {
        return sfc(name);
    }

    public static NewXStrings forResourceId(int id) {
        return new NewXStrings("string/" + id, id);
    }

    public static String str(String name) {
        return str(name, new Object[0]);
    }

    public static String str(String name, Object... args) {
        return read(currentContext(), name, 0, args);
    }

    public static String str(Context context, String name, Object... args) {
        return read(context, name, 0, args);
    }

    private static Context currentContext() {
        Activity activity = ResourceUtils.useActivityContextIfAvailable ? Utils.getActivity() : null;
        return activity == null ? Utils.getContext() : activity;
    }

    private static String read(Context context, String name, int resourceId, Object... args) {
        if (context == null) return name;
        int id = resourceId != 0 ? resourceId
                : context.getResources().getIdentifier(name, "string", context.getPackageName());
        if (id == 0) return name;
        return args != null && args.length == 0 ? context.getString(id) : context.getString(id, args);
    }

    @Override
    public String toString() {
        return read(currentContext(), resourceName, resourceId);
    }
}
