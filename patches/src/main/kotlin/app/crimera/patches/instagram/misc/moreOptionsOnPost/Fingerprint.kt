/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.moreOptionsOnPost

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/feed/MoreOptionsOnPostPatch;"

internal object ContextFieldNameExtensionFingerprint : Fingerprint(
    name = "contextFieldName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object MediaFieldNameExtensionFingerprint : Fingerprint(
    name = "mediaFieldName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object CurrentMediaIndexFieldNameExtensionFingerprint : Fingerprint(
    name = "currentMediaIndexFieldName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)
