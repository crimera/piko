/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.history

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Feed "viewed impression" callback, called once a post has actually been on screen (not for
// prefetched rows). The Media and carousel state arguments are cast in the method body.
private object FeedViewedImpressionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;", "J", "Ljava/lang/Object;"),
    strings = listOf("viewed_impression", "instagram_organic_viewed_impression"),
)

private const val VIEW_HISTORY_HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/history/ViewHistoryHook;"

@Suppress("unused")
val feedViewHistoryPatch =
    bytecodePatch(
        name = "Log feed posts to view history",
        description = "Records each feed post to Piko's view history once it has been on screen.",
        default = true,
    ) {
        dependsOn(viewHistorySettingsPatch, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            FeedViewedImpressionFingerprint.method.apply {
                fun fail(what: String): Nothing = throw PatchException("Feed view history: $what not found")

                fun castIndex(type: String) =
                    instructions.indexOfFirst {
                        it.opcode == Opcode.CHECK_CAST &&
                            ((it as ReferenceInstruction).reference as TypeReference).type == type
                    }.takeIf { it >= 0 } ?: fail("$type cast")

                val mediaCast = castIndex(MEDIA_CLASS_NAME)
                val carouselCast = castIndex(MEDIA_ADD_INFO_CLASS_NAME)
                val mediaReg = (instructions[mediaCast] as OneRegisterInstruction).registerA
                val carouselReg = (instructions[carouselCast] as OneRegisterInstruction).registerA
                val userSessionField =
                    FeedViewedImpressionFingerprint.classDef.fields.firstOrNull { it.type == USER_SESSION_CLASS }
                        ?: fail("user session")

                // Right after both casts, before Instagram's own early return that skips the logging.
                val insertIndex = maxOf(mediaCast, carouselCast) + 1
                val used = mutableListOf(mediaReg, carouselReg)
                fun freeRegister() = findFreeRegister(insertIndex, used).also { used += it }
                val sessionReg = freeRegister()
                val indexReg = freeRegister()
                // iget and non-range invoke only encode v0-v15.
                if (used.any { it > 15 }) fail("low registers")

                addInstructions(
                    insertIndex,
                    """
                    move-object/from16 v$sessionReg, p0
                    iget-object v$sessionReg, v$sessionReg, $userSessionField
                    iget v$indexReg, v$carouselReg, $CURRENT_MEDIA_FIELD
                    invoke-static {v$mediaReg, v$sessionReg, v$indexReg}, $VIEW_HISTORY_HOOK_CLASS->logMediaView(Ljava/lang/Object;Lcom/instagram/common/session/UserSession;I)V
                    """.trimIndent(),
                )
            }
        }
    }
