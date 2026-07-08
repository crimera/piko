/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.autoDownload

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.userProfile.profileMoreOptionsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Same class piko already hooks for story timestamp customisation (see
// CustomiseStoryTimestampPatch.kt) -- an instance method defined directly on
// ReelItem, so p0 = the ReelItem itself. Fires automatically whenever that
// story is rendered on screen, no button press or explicit "view" step.
internal object ReelItemAutoDownloadFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/model/reels/ReelItem;",
    parameters = listOf("Landroid/content/Context;"),
)

// Historically stable, non-obfuscated Instagram user model class name
// (consistent with FRIENDSHIP_STATUS_CLASS = "Lcom/instagram/user/model/FriendshipStatus;"
// already used elsewhere in this repo, same package).
// VERIFY on first build: if ownerField below resolves to null, open ReelItem
// in jadx and confirm/correct this string.
private const val USER_CLASS = "Lcom/instagram/user/model/User;"

@Suppress("unused")
val autoDownloadStoriesPatch =
    bytecodePatch(
        name = "Auto download stories",
        description = "Automatically downloads stories for accounts marked via profile 'More options' whenever their story is shown -- no viewer, no button press needed.",
    ) {
        dependsOn(decoderEntity, profileMoreOptionsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ReelItemAutoDownloadFingerprint.method.apply {
                val classDef = ReelItemAutoDownloadFingerprint.classDef

                val ownerField = classDef.fields.firstOrNull { it.type == USER_CLASS }
                val mediaField = classDef.fields.lastOrNull { it.type == MEDIA_CLASS_NAME }

                requireNotNull(ownerField) { "Could not find User-typed field on ReelItem -- update USER_CLASS in AutoDownloadStoriesPatch.kt" }
                requireNotNull(mediaField) { "Could not find Media-typed field on ReelItem -- MEDIA_CLASS_NAME resolution may have changed" }

                // p0 = ReelItem instance ("this"), p1 = Context (per parameters above).
                // v2/v3 assumed free at method start -- verify no collisions on build.
                addInstructions(
                    0,
                    """
                    iget-object v2, p0, $ownerField
                    iget-object v3, p0, $mediaField
                    invoke-static {p1, v2, v3}, ${PATCHES_DESCRIPTOR}/download/AutoDownloadStories;->checkAndDownloadFromReelItem(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
