/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;

/** How each ephemeral media was sent, by the second it expires at, since the message is saved as permanent. */
public class EphemeralModesPref extends BaseSharedPref {

    private static final EphemeralModesPref INSTANCE = new EphemeralModesPref();
    private static final String KEY = "modes";

    private static JSONObject modes;

    private EphemeralModesPref() {
        super("piko_ephemeral_modes");
    }

    public static synchronized String get(long expireAt) {
        return load().optString(String.valueOf(expireAt), "");
    }

    public static synchronized void put(long expireAt, String mode) {
        try {
            JSONObject current = load();
            // The media of an expired note is gone, so the note is dropped.
            long now = System.currentTimeMillis() / 1000;
            List<String> expired = new ArrayList<>();
            for (Iterator<String> keys = current.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (Long.parseLong(key) < now) expired.add(key);
            }
            for (String key : expired) current.remove(key);

            current.put(String.valueOf(expireAt), mode);
            INSTANCE.setString(KEY, current.toString());
        } catch (JSONException | NumberFormatException ignored) { }
    }

    private static JSONObject load() {
        if (modes == null) {
            try {
                modes = new JSONObject(INSTANCE.getString(KEY, "{}"));
            } catch (JSONException e) {
                modes = new JSONObject();
            }
        }
        return modes;
    }
}
