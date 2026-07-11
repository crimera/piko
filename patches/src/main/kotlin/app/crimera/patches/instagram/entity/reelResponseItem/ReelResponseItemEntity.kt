/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.reelResponseItem

import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

val reelResponseItemEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of ReelResponseItem class",
    ) {
        execute {

            ReelResponseItemFingerprint.apply {
                val reelTypeFieldName = classDef.fields.first { it.type == ReelTypeEnumInitFingerprint.classDef.type }.name
                GetReelTypeExtensionFingerprint.changeFirstString(reelTypeFieldName)
            }
        }
    }
