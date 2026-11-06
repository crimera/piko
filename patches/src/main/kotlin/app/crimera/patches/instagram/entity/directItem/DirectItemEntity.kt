/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val MEDIA_CLASS = "Lcom/instagram/feed/media/Media;"
private const val GIF_URL_CLASS = "Lcom/instagram/model/mediasize/GifUrlImpl;"
private const val GIF_URL_HELPER_CLASS = "Lcom/instagram/model/mediasize/GifUrlImpl__JsonHelper;"

/**
 * The GifUrlImpl String field the JSON writer reads just before it writes [key]. Its parser fills
 * the object through a constructor, so the writer is the only place a field meets its key.
 */
context(patchContext: BytecodePatchContext)
private fun gifUrlField(key: String): String {
    for (method in patchContext.classDefBy(GIF_URL_HELPER_CLASS).methods) {
        val instructions = method.implementation?.instructions?.toList() ?: continue
        stringFieldBeforeKey(instructions, GIF_URL_CLASS, key)?.let { return it }
    }
    throw IllegalStateException("No GifUrlImpl field before \"$key\"")
}

/** In a JSON writer: the String field of [modelClass] read last before the key [key] is written. */
private fun stringFieldBeforeKey(
    instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
    modelClass: String,
    key: String,
): String? {
    val keyIndex = instructions.indexOfFirst {
        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
            it.getReference<StringReference>()?.string == key
    }
    if (keyIndex < 0) return null
    for (i in keyIndex - 1 downTo 0) {
        val field = instructions[i].takeIf { it.opcode == Opcode.IGET_OBJECT }?.getReference<FieldReference>()
        if (field?.definingClass == modelClass && field.type == "Ljava/lang/String;") return field.name
    }
    return null
}

/**
 * Field assigned with the value of the JSON [key] by the parser of [parserClass].
 */
context(_: BytecodePatchContext)
private fun jsonKeyField(key: String, parserClass: String) =
    jsonKeyFieldFingerprint(key, parserClass).instructionMatches.last().fieldReference

/**
 * Index where the shared value assignment of the JSON [key] starts in the parser of [parserClass].
 */
context(_: BytecodePatchContext)
private fun jsonSharedKeyTarget(key: String, parserClass: String) =
    jsonSharedKeyFingerprint(key, parserClass).let {
        it.method to it.method.getInstruction<BuilderOffsetInstruction>(it.instructionMatches.last().index)
            .target.location.index
    }

/**
 * Field of the shared value assignment of the JSON [key] by the parser of [parserClass].
 */
context(_: BytecodePatchContext)
private fun jsonSharedKeyField(key: String, parserClass: String): FieldReference {
    val (parser, target) = jsonSharedKeyTarget(key, parserClass)
    return parser.getInstruction(parser.indexOfFirstInstructionOrThrow(target, Opcode.IPUT_OBJECT))
        .getReference<FieldReference>()!!
}

/**
 * Resolves every obfuscated DirectItem field name at patch time and bakes it into the
 * {@code DirectItem} extension entity, so the runtime never has to discover field/method
 * names by reflection. See {@code mediaDataEntity} for the reference pattern.
 */
val directItemEntity =
    bytecodePatch(
        description = "Decodes obfuscated DirectItem (DM) field names at patch time.",
    ) {
        execute {
            val parserClass = DirectItemDispatchFingerprint.originalClassDef.type

            val itemId = jsonKeyField("item_id", parserClass)
            GetItemIdExtension.changeFirstString(itemId.name)
            GetClientContextExtension.changeFirstString(jsonKeyField("client_context", parserClass).name)
            GetBaseClassNameExtension.changeFirstString(classNameToExtension(itemId.definingClass))

            GetUserIdExtension.changeFirstString(jsonKeyField("user_id", parserClass).name)

            // Text and reaction share their assignment.
            val textField = jsonSharedKeyField("text", parserClass)
            GetTextExtension.changeString("baseTextField", textField.name)
            SetTextExtension.changeString("baseTextField", textField.name)

            GetTimestampRawExtension.changeFirstString(jsonKeyField("timestamp", parserClass).name)

            val hideField = jsonKeyField("hide_in_thread", parserClass).name
            IsHideInThreadExtension.changeFirstString(hideField)
            SetHideInThreadExtension.changeFirstString(hideField)

            IsSentByViewerExtension.changeFirstString(
                sentByViewerFieldFingerprint(parserClass, SentByViewerKeyFingerprint.originalMethod)
                    .instructionMatches.last().fieldReference.name,
            )

            GetThreadKeyExtension.changeFirstString(jsonKeyField("thread_key", parserClass).name)

            val itemTypeField = jsonSharedKeyField("item_type", parserClass)
            GetItemTypeExtension.changeFirstString(itemTypeField.name)

            // Media resolution. Every supported DM media shape carries a com.instagram.feed.media.Media
            // object, either directly or in a wrapper, and is resolved by its JSON key.
            //
            // NOTE: animated_media, story_share, xma and link are intentionally NOT resolved, their
            // URL sits in an obfuscated wrapper with no stable type/getter anchor (e.g. GifUrlImpl:
            // three indistinguishable String fields). Adding them would be fragile to maintain, so
            // those shapes degrade to a "[type]" label at runtime. See DirectItem.getMediaUrl().
            val mediaField = jsonKeyField("media", parserClass)
            // Concrete item class, a subclass of the scalar base class, which is why media must be read off it.
            val itemClass = mediaField.definingClass
            // Class.forName needs the binary name (X.6fW), not the smali descriptor (LX/6fW;).
            GetMediaClassNameExtension.changeFirstString(classNameToExtension(itemClass))
            FieldMediaExtension.changeFirstString(mediaField.name)
            FieldMediaShareExtension.changeFirstString(jsonKeyField("media_share", parserClass).name)
            FieldRavenMediaExtension.changeFirstString(jsonKeyField("raven_media", parserClass).name)

            // Wrapped shapes: wrapper field on the item class, then the Media field of the wrapper.
            listOf(
                Triple("clip", FieldClipExtension, FieldClipMediaExtension),
                Triple("reel_share", FieldReelExtension, FieldReelMediaExtension),
                Triple("voice_media", FieldVoiceExtension, FieldVoiceMediaExtension),
                // Disappearing media: carries the payload that raven_media leaves null.
                Triple("visual_media", FieldVisualExtension, FieldVisualMediaExtension),
            ).forEach { (key, wrapperExtension, mediaExtension) ->
                val wrapperField = jsonKeyField(key, parserClass)
                wrapperExtension.changeFirstString(wrapperField.name)
                mediaExtension.changeFirstString(classDefBy(wrapperField.type).fields.first { it.type == MEDIA_CLASS }.name)
            }

            // MQTT items store text in an Object field on the item class, set after the item type.
            directItemSubTextFingerprint(itemClass, itemTypeField.type).instructionMatches.last().fieldReference.let {
                GetTextExtension.changeString("subTextField", it.name)
                SetTextExtension.changeString("subTextField", it.name)
            }

            // xma reshare permalink. An xma reshare (shared post/reel) has no Media object, instead the item
            // holds a List of xma elements whose permalink is a String under JSON key "target_url".
            // All xma keys share the assignment of the xma List field.
            val (parser, xmaTarget) = jsonSharedKeyTarget("xma_media_share", parserClass)
            val xmaField = parser.getInstruction(
                parser.indexOfFirstInstructionOrThrow(
                    xmaTarget,
                    fieldAccess(type = "Ljava/util/List;", opcode = Opcode.IPUT_OBJECT),
                ),
            ).getReference<FieldReference>()!!
            // The parse call is declared on the abstract base, so take the element parser class from the sget.
            val xmaElementParser = parser.getInstruction(
                parser.indexOfFirstInstructionReversedOrThrow(
                    parser.indexOfFirstInstructionOrThrow(xmaTarget, methodCall(name = "parseFromJsonParser")),
                    Opcode.SGET_OBJECT,
                ),
            ).getReference<FieldReference>()!!.type
            FieldXmaExtension.changeFirstString(xmaField.name)
            FieldXmaLinkExtension.changeFirstString(jsonKeyField("target_url", xmaElementParser).name)
            // Thumbnail of a shared reel/post, for notification images. Optional: without it a share
            // only shows its text, so a version without the key must not fail the whole entity.
            // On 452 it is "preview_url_info", an ExtendedImageUrl (read through ImageUrl.getUrl()).
            runCatching { jsonKeyField("preview_url_info", xmaElementParser) }
                .onSuccess {
                    if (it.type == "Lcom/instagram/model/mediasize/ExtendedImageUrl;" || it.type == "Ljava/lang/String;") {
                        FieldXmaPreviewExtension.changeFirstString(it.name)
                    }
                }
                .onFailure { println("WARNING: xma preview_url_info not found, shared reels and posts get no image") }

            // Sticker/GIF image, for notifications. Optional like the preview above.
            runCatching {
                val animated = jsonKeyField("animated_media", parserClass)
                val gif = classDefBy(animated.type).fields.single { it.type == GIF_URL_CLASS }
                val url = gifUrlField("url")
                val webp = gifUrlField("webp")
                FieldAnimatedExtension.changeFirstString(animated.name)
                AnimatedClassNameExtension.changeFirstString(classNameToExtension(animated.definingClass))
                FieldAnimatedGifExtension.changeFirstString(gif.name)
                // "images": {"fixed_height": GifUrlImpl}, which holds the URL when "gif_url" is empty.
                val images = classDefBy(animated.type).fields.firstOrNull { f ->
                    f.type != GIF_URL_CLASS && f.type.startsWith("LX/") &&
                        runCatching { classDefBy(f.type).fields.any { it.type == GIF_URL_CLASS } }.getOrDefault(false)
                }
                if (images != null) {
                    FieldAnimatedImagesExtension.changeFirstString(images.name)
                    FieldImagesGifExtension.changeFirstString(classDefBy(images.type).fields.first { it.type == GIF_URL_CLASS }.name)
                }
                FieldGifUrlExtension.changeFirstString(url)
                FieldGifWebpExtension.changeFirstString(webp)
            }.onFailure { println("WARNING: sticker image fields not found (${it.message}), stickers get no image") }

            // Instagram's own stickers: item "store_sticker" -> DirectStoreStickerResponseItem "image_url".
            runCatching {
                val store = jsonKeyField("store_sticker", parserClass)
                check(store.type == STORE_STICKER_CLASS) { "store_sticker is ${store.type}" }
                val image = stringFieldBeforeKey(
                    StoreStickerWriterFingerprint.method.implementation!!.instructions.toList(),
                    STORE_STICKER_CLASS,
                    "image_url",
                ) ?: error("no image_url field")
                StoreClassNameExtension.changeFirstString(classNameToExtension(store.definingClass))
                FieldStoreStickerExtension.changeFirstString(store.name)
                FieldStoreImageExtension.changeFirstString(image)
            }.onFailure { println("WARNING: store sticker fields not found (${it.message}), store stickers get no image") }

            GetThreadIdExtension.changeFirstString(
                DirectThreadKeyToStringFingerprint.instructionMatches.first().fieldReference.name,
            )
        }
    }
