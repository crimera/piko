/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.history

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.AccessFlags

private const val REEL_VIEWER_FRAGMENT_CLASS = "Linstagram/features/stories/fragment/ReelViewerFragment;"
private const val REEL_ITEM_CLASS = "Lcom/instagram/model/reels/ReelItem;"

// Called when a story item becomes the one on screen in the story viewer (not when neighbouring
// items are preloaded). Anchored on its own trace name.
private object StoryActiveItemBoundFingerprint : Fingerprint(
    definingClass = REEL_VIEWER_FRAGMENT_CLASS,
    returnType = "V",
    custom = { method, _ -> method.parameterTypes.firstOrNull() == REEL_ITEM_CLASS },
    strings = listOf("ReelViewerFragment.onCurrentActiveItemBound"),
)

private const val VIEW_HISTORY_HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/history/ViewHistoryHook;"

@Suppress("unused")
val storiesViewHistoryPatch =
    bytecodePatch(
        name = "Log stories to view history",
        description = "Records each story item to Piko's view history as it is shown in the story viewer.",
        default = true,
    ) {
        dependsOn(viewHistorySettingsPatch, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            fun fail(what: String): Nothing = throw PatchException("Stories view history: $what not found")

            // The item's Media (null for non-media items such as live). ReelItem has two Media
            // fields; this is the only final one.
            val mediaField =
                classDefBy(REEL_ITEM_CLASS).fields
                    .singleOrNull { it.type == MEDIA_CLASS_NAME && AccessFlags.FINAL.isSet(it.accessFlags) }
                    ?: fail("ReelItem media field")

            StoryActiveItemBoundFingerprint.method.apply {
                val used = mutableListOf<Int>()
                fun freeRegister() = findFreeRegister(0, used).also { used += it }
                val sessionReg = freeRegister()
                val mediaReg = freeRegister()
                val indexReg = freeRegister()
                // iget and non-range invoke only encode v0-v15.
                if (used.any { it > 15 }) fail("low registers")

                // No branches: the extension ignores a null media.
                addInstructions(
                    0,
                    """
                    move-object/from16 v$sessionReg, p0
                    invoke-virtual {v$sessionReg}, $REEL_VIEWER_FRAGMENT_CLASS->getSession()Lcom/instagram/common/session/UserSession;
                    move-result-object v$sessionReg
                    move-object/from16 v$mediaReg, p1
                    iget-object v$mediaReg, v$mediaReg, $mediaField
                    const/4 v$indexReg, 0x0
                    invoke-static {v$mediaReg, v$sessionReg, v$indexReg}, $VIEW_HISTORY_HOOK_CLASS->logMediaView(Ljava/lang/Object;Lcom/instagram/common/session/UserSession;I)V
                    """.trimIndent(),
                )
            }
        }
    }
