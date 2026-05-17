/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.instagramButton

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/InstagramButton;"
internal const val IGDS_BUTTON_CLASS_DESCRIPTOR = "Lcom/instagram/igds/components/button/IgdsButton;"

internal object SetStyleObjectExtensionFingerprint : Fingerprint(
    name = "setStyleObject",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetStyleExtensionFingerprint : Fingerprint(
    name = "setStyle",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object IgdsButtonSetStyleFingerprint : Fingerprint(
    definingClass = IGDS_BUTTON_CLASS_DESCRIPTOR,
    name = "setStyle",
)
