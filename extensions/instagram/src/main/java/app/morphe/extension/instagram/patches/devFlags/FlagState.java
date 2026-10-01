/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.devFlags;

public enum FlagState {
    DEFAULT,
    ENABLE,
    DISABLE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}