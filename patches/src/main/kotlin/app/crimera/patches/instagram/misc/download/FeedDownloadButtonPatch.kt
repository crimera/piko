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
import app.crimera.patches.shared.declaredParameterRegister
import app.crimera.patches.shared.parameterRegisterStart
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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

/** Throws unless [candidates] holds exactly one element. */
internal fun <T> requireOne(
    label: String,
    candidates: Collection<T>,
    describe: (T) -> Any? = { it },
): T =
    candidates.singleOrNull()
        ?: throw PatchException(
            "Expected one $label, found ${candidates.size}: ${candidates.joinToString { describe(it).toString() }}",
        )

internal fun MethodReference.sameSignatureAs(other: MethodReference): Boolean =
    name == other.name &&
        returnType == other.returnType &&
        parameterTypes.map { it.toString() } == other.parameterTypes.map { it.toString() }

internal fun BuilderInstruction.methodRef(): MethodReference? =
    (this as? ReferenceInstruction)?.reference as? MethodReference

/** Register words an invoke or move instruction reads, in operand order. */
internal fun BuilderInstruction.registers(): List<Int> =
    when (this) {
        is FiveRegisterInstruction ->
            listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)

        is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
        is TwoRegisterInstruction -> listOf(registerA, registerB)
        is OneRegisterInstruction -> listOf(registerA)
        else -> emptyList()
    }

internal fun MutableMethod.parameterRegister(descriptor: String): Int {
    val index = parameterTypes.indexOfFirst { it.toString() == descriptor }
    if (index < 0) throw PatchException("Method $this has no $descriptor parameter")
    return declaredParameterRegister(this, index)
}

/** The register block holding `this` and every parameter. */
internal fun MutableMethod.parameterBlock(): List<Int> =
    (parameterRegisterStart(this) until (implementation?.registerCount ?: 0)).toList()

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

/**
 * Extension method whose two placeholder constants are rewritten with the live-index field names.
 * The click handlers call it at click time, so a carousel swipe after the bind is honored.
 */
private object CurrentMediaIndexFingerprint : Fingerprint(
    definingClass = DOWNLOAD_UTILS_DESCRIPTOR,
    name = "currentMediaIndex",
)

private class UserSessionSource(
    val type: String,
    val register: Int,
    val field: FieldReference,
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
            resourceMappingPatch,
            // Loads `SettingsStatus` at startup and registers the download settings screen.
            settingsPatch,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("instagram")

            // Only applied through "Download media", which enables the download section itself.
            enableSettings("feedDownloadButton")

            val saveButtonId = getResourceId(ResourceType.ID, "row_feed_button_save")
            val stateType = hookFeedRowBinder(saveButtonId)
            pinMainFeedToViewUfi()
            injectLithoDownloadButton(
                saveButtonId,
                getResourceId(ResourceType.DRAWABLE, "instagram_download_outline_24"),
                stateType,
            )
        }
    }

/**
 * The feed post action row is a view holder whose constructor resolves `row_feed_button_save`.
 * Matching that literal finds the holder without depending on obfuscated names. Its binder gets a
 * hook that hands the extension the row root, the post `Media`, the `UserSession` and the row
 * state, so the extension can add the button next to the save icon.
 *
 * @return the type of the row state the binder receives.
 */
context(patchContext: BytecodePatchContext)
private fun hookFeedRowBinder(saveButtonId: Long): String {
    val holderMatch =
        Fingerprint(
            name = "<init>",
            returnType = "V",
            parameters = listOf(VIEW_DESCRIPTOR),
            filters = listOf(literal(saveButtonId)),
        ).matchAll(1..1).single()
    val holderClass = holderMatch.classDef
    val holderType = holderClass.type

    // The root view is the only field assigned directly from the constructor's `View` parameter.
    val viewRegister = holderMatch.method.parameterRegister(VIEW_DESCRIPTOR)
    val rootViewField =
        requireOne(
            "root view field of $holderType",
            holderMatch.method.implementation
                ?.instructions
                .orEmpty()
                .mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
                    if ((instruction as TwoRegisterInstruction).registerA != viewRegister) return@mapNotNull null
                    instruction.getReference<FieldReference>()?.takeIf { it.definingClass == holderType }
                }.distinctBy { it.toString() },
        )

    // The row state is the holder field type that exposes exactly one `Media`.
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

    val bindCandidates = mutableListOf<MethodReference>()
    patchContext.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            val parameters = method.parameterTypes.map { it.toString() }
            if (method.returnType == "V" && holderType in parameters && stateType in parameters) {
                bindCandidates.add(method)
            }
        }
    }
    val bindReference = requireOne("feed UFI bind method", bindCandidates)
    val bindClass = patchContext.mutableClassDefBy(bindReference.definingClass)
    val bind = bindClass.methods.first { it.sameSignatureAs(bindReference) }

    // 448 keeps the session in a binder field, 439 passes it through a parameter whose type
    // exposes exactly one `UserSession`. Prefer the binder's own field.
    val binderSessionFields = bindClass.fields.filter { it.type == USER_SESSION_DESCRIPTOR }
    val sessionSource =
        when (binderSessionFields.size) {
            0 ->
                requireOne(
                    "UserSession source of $bind",
                    bind.parameterTypes.indices.mapNotNull { index ->
                        val type = bind.parameterTypes[index].toString()
                        val field =
                            patchContext
                                .classDefByOrNull(type)
                                ?.fields
                                ?.singleOrNull { it.type == USER_SESSION_DESCRIPTOR }
                        field?.let { UserSessionSource(type, declaredParameterRegister(bind, index), it) }
                    },
                )

            1 -> UserSessionSource(bindClass.type, parameterRegisterStart(bind), binderSessionFields.single())
            else -> throw PatchException("Expected at most one UserSession field on ${bindClass.type}")
        }

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
            ${moveObject(userSession, sessionSource.register)}
            iget-object v$userSession, v$userSession, ${sessionSource.field}
            invoke-static {v$rootView, v$media, v$userSession, v$state}, $ADD_FEED_DOWNLOAD_BUTTON
        """.trimIndent(),
    )

    return stateType
}

/**
 * The same post can render its UFI row as a view, a Litho or a Compose component, chosen by a
 * MobileConfig value. Only the view renderer creates the holder [hookFeedRowBinder] extends, so the
 * main feed is pinned to it and every other module keeps the original selector result. 439 splits
 * the selector into a static variant and an instance variant; both are pinned.
 */
context(patchContext: BytecodePatchContext)
private fun pinMainFeedToViewUfi() {
    val selectors =
        Fingerprint(
            returnType = INTEGER_DESCRIPTOR,
            strings = listOf(MAIN_FEED_MODULE, LITHO_UFI_VARIANT, VIEW_UFI_VARIANT),
        ).matchAll(1..2)

    // The selector returns the static `Integer` that follows the "view" string constant.
    val viewVariantField =
        requireOne(
            "view UFI variant field",
            selectors
                .map { selector ->
                    val instructions = selector.method.implementation?.instructions.orEmpty()
                    val viewStringIndex =
                        instructions.indexOfFirst { it.getReference<StringReference>()?.string == VIEW_UFI_VARIANT }
                    if (viewStringIndex < 0) throw PatchException("No \"$VIEW_UFI_VARIANT\" case in ${selector.method}")
                    requireOne(
                        "view UFI variant field of ${selector.method}",
                        instructions
                            .drop(viewStringIndex + 1)
                            .take(8)
                            .mapNotNull { instruction ->
                                if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
                                instruction.getReference<FieldReference>()?.takeIf { it.type == INTEGER_DESCRIPTOR }
                            }.distinctBy { it.toString() },
                    )
                }.distinctBy { it.toString() },
        )

    selectors.forEach { selector ->
        val method = selector.method
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
