/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.utils;

import java.io.IOException;
import java.util.concurrent.Callable;

public final class OfflineRequest {
    private OfflineRequest() {}

    public static final Callable<Void> REQUEST = () -> {
        throw new IOException("Content disabled in Piko settings");
    };
}
