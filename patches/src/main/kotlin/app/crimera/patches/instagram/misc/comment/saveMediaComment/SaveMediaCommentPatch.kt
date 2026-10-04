/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.saveMediaComment

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.nativeChatButtonResources
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val saveMediaCommentPatch =
    bytecodePatch(
        name = "Save media comment",
        description = "Adds a button to save media comments on posts and reels.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            addCommentPatch,
            commentButtonClickCheckPatch,
            commentDataEntity,
            mediaDataEntity,
            decoderEntity,
            debugCommentPatch,
        )
        execute {

            val actionMatch = ChatButtonActionsFingerprint.matchAll(1..1).single()
            val actionCode = actionMatch.method.instructions
            val marker = actionCode.withIndex().singleOrNull { (_, instruction) ->
                instruction.getReference<StringReference>()?.string == "SAVE_MEDIA"
            }?.index ?: throw PatchException("Expected one SAVE_MEDIA action marker")
            val creator = actionCode.getOrNull(marker + 1)?.getReference<MethodReference>()
            val result = actionCode.getOrNull(marker + 2)
            val store = actionCode.getOrNull(marker + 3)
            val action = store?.getReference<FieldReference>()
                ?: throw PatchException("Expected a native SAVE_MEDIA action field store")
            if (creator?.returnType != actionMatch.classDef.type ||
                result?.opcode != Opcode.MOVE_RESULT_OBJECT || store?.opcode != Opcode.SPUT_OBJECT ||
                result.registersUsed != store.registersUsed || action.type != creator.returnType ||
                action.definingClass != action.type
            ) {
                throw PatchException("Could not resolve the native SAVE_MEDIA action field")
            }
            val constructor = SaveMediaChatButtonInitFingerprint(action).matchAll(1..1).single().method
            val (stringLateral, drawableLateral) = nativeChatButtonResources(constructor)

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitSaveMediaButtonExtensionFingerprint,
                InitSaveMediaButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitSaveMediaButtonExtensionFingerprint)

            enableSettings("saveMediaCommentButton")
        }
    }
