/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

private const val INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR = "$INSTANTS_DESCRIPTOR/InstantsDownloadHook;"

/**
 * "Save Instants": records each view-once Instant so it can be re-viewed and downloaded afterwards.
 *
 * The hook goes on the constructor of the app's per-instant item, which takes that instant's Media
 * as its first argument — so every instant is caught as it is built, and the hook is handed the
 * Media rather than having to reach for it.
 */
@Suppress("unused")
val instantsDownloadPatch =
    bytecodePatch(
        name = "Save Instants",
        description = "Captures view-once Instants as you view them so you can re-view and download them later.",
    ) {
        dependsOn(settingsPatch, instantsDownloadResourcePatch, mediaDataEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val itemType = (InstantItemFingerprint.matchOrNull()
                ?: InstantMediaItemFingerprint.matchSingle()).originalClassDef.type
            val constructor = instantItemConstructorFingerprint(itemType).matchSingle().method
            if (constructor.parameterTypes.firstOrNull() != MEDIA_CLASS_NAME) {
                throw PatchException("Expected the instant item's first constructor parameter to be Media")
            }
            // p0 is the item being constructed, p1 its Media.
            constructor.addInstruction(
                0,
                "invoke-static/range {p1 .. p1}, $INSTANTS_DOWNLOAD_HOOK_DESCRIPTOR->noteInstantMedia(Ljava/lang/Object;)V",
            )
            enableSettings("instantsDownload")
        }
    }
