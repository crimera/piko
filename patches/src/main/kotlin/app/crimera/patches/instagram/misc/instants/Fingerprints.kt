/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.utils.Constants.INSTANTS_DESCRIPTOR
import app.morphe.patcher.Fingerprint

/** Anchors InstantsDownloadHook.names(), whose returned array holds the sentinel placeholders for
 *  the obfuscated field/method names the received-instant download path reflects on. An array-return
 *  method (not static fields) so R8 can't constant-fold the placeholders away before patch time.
 *  Rewritten at patch time (§11) by instantsDownloadPatch by matching each sentinel's text. */
internal object InstantsDownloadNamesFingerprint : Fingerprint(
    definingClass = "$INSTANTS_DESCRIPTOR/InstantsDownloadHook;",
    name = "names",
)
