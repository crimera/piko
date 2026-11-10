/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SettingRegistry {
    private static final Map<String, RestartPolicy> restartPolicies = new ConcurrentHashMap<>();

    private SettingRegistry() {
    }

    static void register(String key, RestartPolicy restartPolicy) {
        Objects.requireNonNull(restartPolicy);
        RestartPolicy previous = restartPolicies.putIfAbsent(key, restartPolicy);
        // Dynamic flags and Focus Lock selections can recreate the same setting.
        if (previous != null && previous != restartPolicy) {
            throw new IllegalArgumentException("Conflicting restart policy for setting: " + key);
        }
    }

    public static RestartPolicy restartPolicyFor(String key) {
        return Objects.requireNonNull(restartPolicies.get(key),
                "No restart policy declared for setting: " + key);
    }
}
