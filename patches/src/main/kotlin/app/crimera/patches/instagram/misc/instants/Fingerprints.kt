/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.morphe.patcher.Fingerprint

/**
 * The app's per-instant item, which has the view modes of an instant.
 */
internal object InstantItemFingerprint : Fingerprint(
    name = "hashCode",
    strings = listOf("NUX_VIDEO", "ONCE", "PERMANENT", "REPLAYABLE"),
)

/**
 * The per-instant item is built from that instant's Media, its first parameter. Hooking its constructor catches every
 * instant as it is created and hands the Media straight to the extension.
 */
internal object InstantItemConstructorFingerprint : Fingerprint(
    classFingerprint = InstantItemFingerprint,
    name = "<init>",
)
