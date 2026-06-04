/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.commentDataEntity

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/CommentData;"

internal object GetTextExtension : Fingerprint(
    name = "getText",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)
