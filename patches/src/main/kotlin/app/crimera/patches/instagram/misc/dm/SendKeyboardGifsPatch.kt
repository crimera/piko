/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/dm/KeyboardGif;"
private const val PHOTO_ENDPOINT = "direct_v2/threads/broadcast/photo_attachment/"

private const val INPUT_CONTENT_INFO_CLASS = "Landroid/view/inputmethod/InputContentInfo;"
private const val BITMAP_CLASS = "Landroid/graphics/Bitmap;"
private const val STRING_CLASS = "Ljava/lang/String;"
private const val COMPOSER_TEXT_VIEW_CLASS = "Lcom/instagram/ui/widget/textview/IgAutoCompleteTextView;"

/**
 * The DM composer's listener for content committed by the keyboard.
 * It shows the "Tap the sticker icon to find and send GIFs." toast when the content is a GIF.
 */
private object ComposerContentListenerFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        string("image/gif"),
        methodCall(definingClass = "Landroid/content/ClipDescription;", name = "hasMimeType"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        string("unsupportedGifError"),
        // Starts the background task for content that is not a GIF.
        methodCall(name = "<init>", parameters = listOf("L", COMPOSER_TEXT_VIEW_CLASS, "L")),
    ),
)

/**
 * Background task that decodes the committed content to a bitmap, releases the URI permission,
 * and posts the runnable of [pastedPhotoSendFingerprint] to the main thread.
 *
 * Other surfaces have tasks of the same shape, so the class comes from [ComposerContentListenerFingerprint].
 */
private fun pastedContentDecodeFingerprint(definingClass: String) = Fingerprint(
    definingClass = definingClass,
    name = "run",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = INPUT_CONTENT_INFO_CLASS, name = "getContentUri"),
        methodCall(definingClass = INPUT_CONTENT_INFO_CLASS, name = "releasePermission"),
        methodCall(name = "<init>", parameters = listOf(BITMAP_CLASS, "L")),
    ),
)

/**
 * Main thread runnable that saves the bitmap to a temp file and sends the file as a photo.
 *
 * The class comes from the runnable that [pastedContentDecodeFingerprint] constructs.
 */
private fun pastedPhotoSendFingerprint(definingClass: String) = Fingerprint(
    definingClass = definingClass,
    name = "run",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("direct_temp_photo"),
        // Saves the bitmap as directory/name.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf(BITMAP_CLASS, USER_SESSION_CLASS, STRING_CLASS, STRING_CLASS),
            returnType = "L",
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
    ),
)

/**
 * Builds the pending media for a photo sent to a DM, from the saved photo file.
 */
private object DirectPhotoPendingMediaFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf(
        USER_SESSION_CLASS,
        "L",
        "Lcom/instagram/pendingmedia/model/constants/ShareType;",
        "L",
        "Ljava/lang/Integer;",
        STRING_CLASS,
        STRING_CLASS,
        "Z",
    ),
    filters = listOf(
        // Stores the photo file path in the pending media.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = STRING_CLASS),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = STRING_CLASS, location = MatchAfterImmediately()),
        // Guesses the mime type from the photo file path.
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf(STRING_CLASS),
            returnType = STRING_CLASS,
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = STRING_CLASS, location = MatchAfterImmediately()),
    ),
)

/**
 * Static initializer of the media type enum. The second filter is the write of its ANIMATED_MEDIA constant.
 */
private object MediaTypeEnumFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("TEXT_POST_GIF", "AVATAR_STICKER"),
    filters = listOf(
        string("ANIMATED_MEDIA"),
        opcode(Opcode.SPUT_OBJECT),
    ),
)

/**
 * Builds the request that sends an uploaded photo, video or animated image to a DM thread.
 */
private object DirectMediaSendRequestFingerprint : Fingerprint(
    filters = listOf(
        string("direct_v2/threads/broadcast/animated_image_attachment/"),
    ),
)

/**
 * How it works:
 *
 * The DM composer accepts images from the keyboard but refuses GIFs with a toast. Images take this path:
 * decode to a bitmap -> save as a temp JPEG -> build a pending media for the file -> upload and send as a photo.
 *
 * Instagram can already upload a pending media as an animated image: the uploader switches to its animated
 * route on the pending media's type. So a GIF is let down the image path, its bytes are written beside the temp
 * JPEG, and the pending media built for that JPEG is given the GIF file and typed as animated media. The send
 * request would then go to an animated endpoint that the server no longer has, so it is pointed at the photo
 * endpoint.
 */
@Suppress("unused")
val sendKeyboardGifsPatch = patch(
    name = "Send keyboard GIFs",
    description = "Sends GIFs picked in the keyboard's GIF picker as animated images in DMs",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        // Let GIFs take the same path as other images instead of showing the toast.
        ComposerContentListenerFingerprint.let {
            val isGifMatch = it.instructionMatches[2]
            val isGifRegister = isGifMatch.getInstruction<OneRegisterInstruction>().registerA

            it.method.addInstructions(
                isGifMatch.index + 1,
                """
                    invoke-static {v$isGifRegister}, $EXTENSION_CLASS->isUnsupportedGif(Z)Z
                    move-result v$isGifRegister
                """,
            )
        }

        // Keep the GIF bytes while the URI can still be read, keyed by the bitmap that is handed on.
        val decodeTaskClass = ComposerContentListenerFingerprint.instructionMatches[5]
            .instruction.getReference<MethodReference>()!!.definingClass
        val decodeFingerprint = pastedContentDecodeFingerprint(decodeTaskClass)

        decodeFingerprint.let {
            val releaseMatch = it.instructionMatches[1]
            val infoRegister = releaseMatch.getInstruction<FiveRegisterInstruction>().registerC
            val bitmapRegister = it.instructionMatches[2].getInstruction<FiveRegisterInstruction>().registerD

            it.method.addInstruction(
                releaseMatch.index,
                "invoke-static {v$infoRegister, v$bitmapRegister}, $EXTENSION_CLASS->capture(${INPUT_CONTENT_INFO_CLASS}${BITMAP_CLASS})V",
            )
        }

        // Write the GIF bytes beside the JPEG that Instagram saved.
        val sendRunnableClass = decodeFingerprint.instructionMatches[2]
            .instruction.getReference<MethodReference>()!!.definingClass

        pastedPhotoSendFingerprint(sendRunnableClass).let {
            val save = it.instructionMatches[1].getInstruction<FiveRegisterInstruction>()
            val bitmapRegister = save.registerC
            val directoryRegister = save.registerE
            val nameRegister = save.registerF

            it.method.addInstruction(
                it.instructionMatches[2].index + 1,
                "invoke-static {v$bitmapRegister, v$directoryRegister, v$nameRegister}, $EXTENSION_CLASS->afterSave(${BITMAP_CLASS}${STRING_CLASS}${STRING_CLASS})V",
            )
        }

        // Give the pending media built for that JPEG the GIF file, and type it as animated media.
        DirectPhotoPendingMediaFingerprint.let {
            val animatedMedia = MediaTypeEnumFingerprint.instructionMatches[1]
                .instruction.getReference<FieldReference>()!!

            val pendingMediaType = it.method.returnType
            val mediaTypeFields = classDefBy(pendingMediaType).instanceFields.filter { field ->
                field.type == animatedMedia.definingClass
            }
            val mediaTypeField = mediaTypeFields.singleOrNull() ?: throw PatchException(
                "Expected one media type field in $pendingMediaType, found ${mediaTypeFields.size}",
            )
            val pathField = it.instructionMatches[2].instruction.getReference<FieldReference>()!!

            val mimeTypeMatch = it.instructionMatches[3]
            val pathRegister = mimeTypeMatch.getInstruction<FiveRegisterInstruction>().registerC
            val pendingMediaRegister = it.instructionMatches[5].getInstruction<TwoRegisterInstruction>().registerB
            val index = mimeTypeMatch.index
            val freeRegister = it.method.findFreeRegister(index, pathRegister, pendingMediaRegister)

            // The path register is replaced too, so the mime type that follows is guessed from the GIF file.
            it.method.addInstructionsWithLabels(
                index,
                """
                    invoke-static {v$pathRegister}, $EXTENSION_CLASS->keyboardGifPath($STRING_CLASS)$STRING_CLASS
                    move-result-object v$freeRegister
                    if-eqz v$freeRegister, :not_keyboard_gif
                    move-object v$pathRegister, v$freeRegister
                    iput-object v$pathRegister, v$pendingMediaRegister, $pathField
                    sget-object v$freeRegister, $animatedMedia
                    iput-object v$freeRegister, v$pendingMediaRegister, $pendingMediaType->${mediaTypeField.name}:${mediaTypeField.type}
                """,
                ExternalLabel("not_keyboard_gif", it.method.getInstruction(index)),
            )
        }

        // Instagram's server answers 404 on the animated endpoint. The photo endpoint accepts the animated upload.
        DirectMediaSendRequestFingerprint.let {
            val endpointMatch = it.instructionMatches[0]
            val endpointRegister = endpointMatch.getInstruction<OneRegisterInstruction>().registerA

            it.method.addInstruction(
                endpointMatch.index + 1,
                "const-string v$endpointRegister, \"$PHOTO_ENDPOINT\"",
            )
        }

        enableSettings("sendKeyboardGifs")
    }
}
