/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
