/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.resource.ResourceType
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method

internal const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
internal const val INTEGER_DESCRIPTOR = "Ljava/lang/Integer;"
internal const val USER_SESSION_DESCRIPTOR = "Lcom/instagram/common/session/UserSession;"
internal const val DOWNLOAD_UTILS_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/DownloadUtils;"

private const val VIEW_DESCRIPTOR = "Landroid/view/View;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val ADD_FEED_DOWNLOAD_BUTTON =
    "$DOWNLOAD_UTILS_DESCRIPTOR->addFeedDownloadButton" +
        "(Landroid/view/View;Ljava/lang/Object;$USER_SESSION_DESCRIPTOR$OBJECT_DESCRIPTOR)V"

/** The UFI renderer selector is only overridden for the main feed module. */
private const val MAIN_FEED_MODULE = "feed_timeline"

/** MobileConfig values the UFI renderer selector switches on. */
private const val LITHO_UFI_VARIANT = "litho"
private const val VIEW_UFI_VARIANT = "view"

internal fun <T> requireOne(
    label: String,
    candidates: Collection<T>,
    describe: (T) -> Any? = { it },
): T =
    candidates.singleOrNull()
        ?: throw PatchException(
            "Expected one $label, found ${candidates.size}: ${candidates.joinToString { describe(it).toString() }}",
        )

internal fun MutableMethod.parameterRegister(descriptor: String): Int {
    val index = parameterTypes.indexOfFirst { it.toString() == descriptor }
    if (index < 0) throw PatchException("Method $this has no $descriptor parameter")
    return p0Register + (if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1) +
        parameterTypes.take(index).sumOf { if (it.toString() == "J" || it.toString() == "D") 2 else 1 }
}

internal fun MutableMethod.parameterBlock(): List<Int> =
    (p0Register until (implementation?.registerCount ?: 0)).toList()

/**
 * Reserves [count] distinct registers that are dead at [index] and no higher than [maximum]
 * (15 for 4-bit operands, 255 for 8-bit ones).
 */
internal fun MutableMethod.reserveFreeRegisters(
    index: Int,
    count: Int,
    maximum: Int,
    excluded: Collection<Int> = parameterBlock(),
): List<Int> {
    val skipped = excluded.toMutableList()
    val reserved = mutableListOf<Int>()
    while (reserved.size < count) {
        val register =
            try {
                findFreeRegister(index, skipped)
            } catch (_: IllegalArgumentException) {
                throw PatchException("Only ${reserved.size} of $count free registers <= v$maximum at index $index of $this")
            }
        skipped += register
        if (register <= maximum) reserved += register
    }
    return reserved
}

// `/16` forms accept any register, so values can be staged into and out of the high registers
// of a dense method.
internal fun moveObject(
    destination: Int,
    source: Int,
) = "move-object/16 v$destination, v$source"

internal fun moveInt(
    destination: Int,
    source: Int,
) = "move/16 v$destination, v$source"

internal fun registerRange(
    first: Int,
    last: Int = first,
) = "{v$first .. v$last}"

/** Extension method whose two placeholder constants are rewritten with the live-index field names. */
private object CurrentMediaIndexFingerprint : Fingerprint(
    definingClass = DOWNLOAD_UTILS_DESCRIPTOR,
    name = "currentMediaIndex",
)

private fun feedRowHolderConstructorFingerprint(saveButtonId: Long) = Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf(VIEW_DESCRIPTOR),
    filters = listOf(
        methodCall(name = "<init>", opcode = Opcode.INVOKE_DIRECT),
        // Root view.
        fieldAccess(
            definingClass = "this",
            type = VIEW_DESCRIPTOR,
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately(),
        ),
        literal(saveButtonId),
    ),
)

private fun feedUfiBindFingerprint(holderType: String, stateType: String) = Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", stateType, holderType),
)

/** The UFI renderer selector, which returns the static `Integer` that follows the "view" string constant. */
private object FeedUfiRendererSelectorFingerprint : Fingerprint(
    returnType = INTEGER_DESCRIPTOR,
    strings = listOf(MAIN_FEED_MODULE, LITHO_UFI_VARIANT),
    filters = listOf(
        string(VIEW_UFI_VARIANT),
        fieldAccess(type = INTEGER_DESCRIPTOR, opcode = Opcode.SGET_OBJECT, location = MatchAfterWithin(7)),
    ),
)

val feedDownloadButtonPatch =
    bytecodePatch {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            sharedExtensionPatch,
            // The download options dialog is an `InstagramDialogBox` wrapper; without this
            // entity its placeholder names are never resolved.
            instagramDialogBoxEntity,
            mediaDataEntity,
            videoDataEntity,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            decoderEntity,
            // Loads `SettingsStatus` at startup and registers the download settings screen.
            settingsPatch,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("instagram")

            // Only applied through "Download media", which enables the download section itself.
            enableSettings("feedDownloadButton")

            val saveButtonId = resourceId(ResourceType.ID, "row_feed_button_save")
            val stateType = hookFeedRowBinder(saveButtonId)
            pinMainFeedToViewUfi()
            injectLithoDownloadButton(
                saveButtonId,
                resourceId(ResourceType.DRAWABLE, "instagram_download_outline_24"),
                stateType,
            )
        }
    }

/**
 * Finds the feed row view holder through the `row_feed_button_save` literal and hooks its binder to
 * hand the extension the row root, `Media`, `UserSession` and row state.
 *
 * @return the type of the row state the binder receives.
 */
context(patchContext: BytecodePatchContext)
private fun hookFeedRowBinder(saveButtonId: Long): String {
    val holderMatch = feedRowHolderConstructorFingerprint(saveButtonId).matchSingle()
    val holderClass = holderMatch.originalClassDef
    val holderType = holderClass.type
    val rootViewField = holderMatch.instructionMatches[1].fieldReference

    val stateType =
        requireOne(
            "feed UFI state type of $holderType",
            holderClass.fields
                .map { it.type }
                .filter { type ->
                    patchContext.classDefByOrNull(type)?.fields?.count { it.type == MEDIA_DESCRIPTOR } == 1
                }.distinct(),
        )
    val stateClass = patchContext.classDefBy(stateType)
    val mediaField = requireOne("Media field of $stateType", stateClass.fields.filter { it.type == MEDIA_DESCRIPTOR })

    // The state reaches the view state the carousel mutates. Its current-index field is the one
    // the overflow-menu handler passes, so the extension reads both at click time.
    val viewStateField =
        requireOne(
            "view state field of $stateType",
            stateClass.fields.filter { it.type == MEDIA_ADD_INFO_CLASS_NAME },
        )
    CurrentMediaIndexFingerprint.changeString("feedViewStateField", viewStateField.name)
    CurrentMediaIndexFingerprint.changeString("feedCurrentMediaField", CURRENT_MEDIA_FIELD.name)

    val bind = feedUfiBindFingerprint(holderType, stateType).matchSingle().method
    val sessionField = requireOne(
        "UserSession field of ${bind.definingClass}",
        patchContext.classDefBy(bind.definingClass).fields.filter { it.type == USER_SESSION_DESCRIPTOR },
    )

    val holderRegister = bind.parameterRegister(holderType)
    val stateRegister = bind.parameterRegister(stateType)
    val (rootView, media, userSession, state) = bind.reserveFreeRegisters(0, 4, maximum = 15)

    // 4-bit `iget` cannot address the argument registers of a method with many locals, so every
    // operand is moved into a low scratch register first.
    bind.addInstructions(
        0,
        """
            ${moveObject(rootView, holderRegister)}
            iget-object v$rootView, v$rootView, $rootViewField
            ${moveObject(state, stateRegister)}
            iget-object v$media, v$state, $mediaField
            ${moveObject(userSession, bind.p0Register)}
            iget-object v$userSession, v$userSession, $sessionField
            invoke-static {v$rootView, v$media, v$userSession, v$state}, $ADD_FEED_DOWNLOAD_BUTTON
        """.trimIndent(),
    )

    return stateType
}

/**
 * Only the view UFI renderer creates the holder [hookFeedRowBinder] hooks, so the main feed is
 * pinned to it; other modules keep the original selector result.
 */
context(patchContext: BytecodePatchContext)
private fun pinMainFeedToViewUfi() {
    FeedUfiRendererSelectorFingerprint.let {
        val viewVariantField = it.instructionMatches.last().fieldReference
        val method = it.method
        val moduleRegister = method.parameterRegister(STRING_DESCRIPTOR)
        val (expected, module) = method.reserveFreeRegisters(0, 2, maximum = 15)

        method.addInstructionsWithLabels(
            0,
            """
                const-string v$expected, "$MAIN_FEED_MODULE"
                ${moveObject(module, moduleRegister)}
                invoke-virtual {v$expected, v$module}, $STRING_DESCRIPTOR->equals($OBJECT_DESCRIPTOR)Z
                move-result v$expected
                if-eqz v$expected, :original
                sget-object v$expected, $viewVariantField
                return-object v$expected
            """.trimIndent(),
            ExternalLabel("original", method.getInstruction(0)),
        )
    }
}
