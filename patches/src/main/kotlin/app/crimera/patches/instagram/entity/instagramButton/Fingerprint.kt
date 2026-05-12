/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
