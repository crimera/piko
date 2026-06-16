/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.debugComment

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i

@Suppress("unused")
val debugCommentPatch =
    bytecodePatch(
        description = "Adds a debug button to comments on posts and reels.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch, addCommentPatch, commentButtonClickCheckPatch, commentDataEntity, resourceMappingPatch, decoderEntity)
        execute {

            // Temp use "Source" as string.
            // TODO Need to be changed to "Debug"
            var stringLateral: Long
            ViewSourcesChatButtonToStringFingerprint.classDef.methods.first { it.name == "<init>" }.apply {
                stringLateral = (instructions.last { it.opcode == Opcode.CONST } as Instruction31i).wideLiteral
            }
            var drawableLateral: Long = getResourceId(ResourceType.DRAWABLE, "instagram_app_instagram_pano_outline_24")

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitDebugButtonExtensionFingerprint,
                InitDebugButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitDebugButtonExtensionFingerprint)
        }
    }
