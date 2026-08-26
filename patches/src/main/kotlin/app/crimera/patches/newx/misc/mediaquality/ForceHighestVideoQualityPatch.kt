/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.mediaquality

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.injectReadWithDefault
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MAXIMUM_VIDEO_DIMENSION = 0x7fffffff
private const val HEIGHT_DIVISOR = 2
private const val TRACK_SELECTION_PARAMETERS_PREFIX = "Landroidx/media3/common/"

// Obfuscated media3 TrackSelectionParameters classes churn between releases
// (u0$b -> v0$b -> ...); only the package is stable, so match by prefix and
// discover the concrete builder class from the matched instruction.

private val VIDEO_QUALITY_LOG_STRINGS =
    listOf(
        "renditionSizing=",
        ", maxHeight=",
        "VideoPlayer",
    )

private object BetaVideoQualityFingerprint : Fingerprint(
    definingClass = "Lcom/x/media/playback/",
    strings = VIDEO_QUALITY_LOG_STRINGS,
    custom = { method, classDef ->
        !classDef.type.contains("/scribing/") &&
            !classDef.type.contains("/ui/") &&
            hasBetaVideoQualityShape(method.implementation?.instructions?.toList().orEmpty())
    },
)

private object AlphaVideoQualityFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/foundation/text/",
    strings = VIDEO_QUALITY_LOG_STRINGS,
    custom = { method, _ ->
        hasAlphaVideoQualityShape(method.implementation?.instructions?.toList().orEmpty())
    },
)

private object AudioTrackOverrideFingerprint : Fingerprint(
    definingClass = "Lcom/x/media/playback/",
    filters = listOf(
        string("Audio bitrates are known, skipping override"),
        string("audio-(\\d+)"),
    ),
    custom = { _, classDef ->
        !classDef.type.contains("/scribing/") && !classDef.type.contains("/ui/")
    },
)

private object MediaBitrateLimiterFingerprint : Fingerprint(
    definingClass = "Lcom/x/media/playback/",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Ljava/lang/Math;",
            name = "min",
            parameters = listOf("I", "I"),
            returnType = "I",
        ),
    ),
    custom = { method, classDef ->
        method.name == "invokeSuspend" &&
            !classDef.type.contains("/scribing/") &&
            !classDef.type.contains("/ui/")
    },
)

private fun hasBetaVideoQualityShape(instructions: List<Instruction>): Boolean {
    val divisionIndex =
        instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.DIV_INT_LIT8 &&
                isNarrowLiteral(instruction, HEIGHT_DIVISOR)
        }
    if (divisionIndex == -1) return false
    if (instructions.none { instruction -> isNarrowLiteral(instruction, MAXIMUM_VIDEO_DIMENSION) }) {
        return false
    }

    val gCallIndex =
        findNextInstructionIndex(instructions, divisionIndex + 1) { instruction ->
            isTrackSelectionBuilderCall(instruction, "g")
        }
    if (gCallIndex == -1) return false
    val builderClass = trackSelectionBuilderClass(instructions[gCallIndex]) ?: return false

    val fCallIndex =
        findNextInstructionIndex(instructions, gCallIndex + 1) { instruction ->
            isTrackSelectionBuilderCall(instruction, "f", builderClass)
        }
    if (fCallIndex == -1) return false

    val viewportWidthIndex =
        findNextFieldWriteIndex(
            instructions,
            fCallIndex + 1,
            builderClass,
            "f",
        )
    if (viewportWidthIndex == -1) return false

    val viewportHeightIndex =
        findNextFieldWriteIndex(
            instructions,
            viewportWidthIndex + 1,
            builderClass,
            "g",
        )
    if (viewportHeightIndex == -1) return false

    return hasViewportReclampShape(instructions, fCallIndex + 1, viewportWidthIndex)
}

private fun hasAlphaVideoQualityShape(instructions: List<Instruction>): Boolean {
    val divisionIndex =
        instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.DIV_INT_LIT8 &&
                isNarrowLiteral(instruction, HEIGHT_DIVISOR)
        }
    if (divisionIndex == -1) return false

    val eWriteIndex =
        findNextInstructionIndex(instructions, divisionIndex + 1) { instruction ->
            isTrackSelectionFieldWrite(instruction, "e")
        }
    if (eWriteIndex == -1) return false
    val builderClass = fieldWriteBuilderClass(instructions[eWriteIndex], "e") ?: return false

    val maximumIndex =
        findNextInstructionIndex(instructions, eWriteIndex + 1) { instruction ->
            isNarrowLiteral(instruction, MAXIMUM_VIDEO_DIMENSION)
        }
    if (maximumIndex == -1) return false

    val widthWriteIndex =
        findNextFieldWriteIndex(
            instructions,
            maximumIndex + 1,
            builderClass,
            "a",
        )
    if (widthWriteIndex == -1) return false

    val heightWriteIndex =
        findNextFieldWriteIndex(
            instructions,
            widthWriteIndex + 1,
            builderClass,
            "b",
        )
    if (heightWriteIndex == -1) return false

    val viewportWidthIndex =
        findNextFieldWriteIndex(
            instructions,
            heightWriteIndex + 1,
            builderClass,
            "f",
        )
    if (viewportWidthIndex == -1) return false

    val viewportHeightIndex =
        findNextFieldWriteIndex(
            instructions,
            viewportWidthIndex + 1,
            builderClass,
            "g",
        )
    if (viewportHeightIndex == -1) return false

    return hasViewportReclampShape(instructions, heightWriteIndex + 1, viewportWidthIndex)
}

private fun isNarrowLiteral(
    instruction: Instruction,
    value: Int,
): Boolean =
    (instruction as? NarrowLiteralInstruction)?.narrowLiteral == value

private fun trackSelectionBuilderClass(instruction: Instruction): String? {
    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return null

    val reference = instruction.getReference<MethodReference>() ?: return null
    if (!reference.definingClass.startsWith(TRACK_SELECTION_PARAMETERS_PREFIX)) return null
    if (reference.parameterTypes != listOf("I") || reference.returnType != "V") return null
    return reference.definingClass
}

private fun isTrackSelectionBuilderCall(
    instruction: Instruction,
    methodName: String,
    builderClass: String? = null,
): Boolean {
    val resolvedClass = trackSelectionBuilderClass(instruction) ?: return false
    val expectedClass = builderClass ?: resolvedClass
    val reference = instruction.getReference<MethodReference>() ?: return false
    return resolvedClass == expectedClass && reference.name == methodName
}

private fun fieldWriteBuilderClass(instruction: Instruction, fieldName: String): String? {
    if (instruction.opcode != Opcode.IPUT) return null

    val reference = instruction.getReference<FieldReference>() ?: return null
    if (!reference.definingClass.startsWith(TRACK_SELECTION_PARAMETERS_PREFIX)) return null
    if (reference.name != fieldName || reference.type != "I") return null
    return reference.definingClass
}

private fun isTrackSelectionFieldWrite(
    instruction: Instruction,
    fieldName: String,
    builderClass: String? = null,
): Boolean {
    val resolvedClass = fieldWriteBuilderClass(instruction, fieldName) ?: return false
    val expectedClass = builderClass ?: resolvedClass
    return resolvedClass == expectedClass
}

private fun isIntegerFieldWrite(
    instruction: Instruction,
    definingClass: String,
    fieldName: String,
): Boolean {
    if (instruction.opcode != Opcode.IPUT) return false

    val reference = instruction.getReference<FieldReference>() ?: return false
    return reference.definingClass == definingClass &&
        reference.name == fieldName &&
        reference.type == "I"
}

private fun findNextInstructionIndex(
    instructions: List<Instruction>,
    startIndex: Int,
    predicate: (Instruction) -> Boolean,
): Int {
    if (startIndex >= instructions.size) return -1
    return (startIndex until instructions.size)
        .firstOrNull { index -> predicate(instructions[index]) }
        ?: -1
}

private fun findNextFieldWriteIndex(
    instructions: List<Instruction>,
    startIndex: Int,
    definingClass: String,
    fieldName: String,
): Int =
    findNextInstructionIndex(instructions, startIndex) { instruction ->
        isIntegerFieldWrite(instruction, definingClass, fieldName)
    }

private fun hasViewportReclampShape(
    instructions: List<Instruction>,
    startIndex: Int,
    endIndex: Int,
): Boolean {
    if (startIndex >= endIndex) return false

    val hasShift =
        (startIndex until endIndex).any { index ->
            instructions[index].opcode == Opcode.SHR_LONG ||
                instructions[index].opcode == Opcode.SHR_LONG_2ADDR
        }
    if (!hasShift) return false

    return (startIndex until endIndex).any { index ->
        instructions[index].opcode == Opcode.AND_LONG ||
            instructions[index].opcode == Opcode.AND_LONG_2ADDR
    }
}

@Suppress("unused")
val newXForceHighestVideoQualityPatch =
    bytecodePatch(
        name = "NewX: Force highest video/audio quality",
        description =
            "Forces video playback to always select the highest available video and audio stream quality without adaptive quality downscaling or network bitrate caps.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val forceHighestQualitySetting =
            newXToggle(
                id = "newx.post_actions_media.force_highest_video_quality",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_force_highest_video_quality"),
                order = 150,
                defaultValue = true,
            )

        execute {
            val audioTrackMatches = AudioTrackOverrideFingerprint.scopedMatchAll()
            if (audioTrackMatches.size != 1) {
                throw PatchException(
                    "Expected exactly one AudioTrackOverride match, found ${audioTrackMatches.size}: " +
                        audioTrackMatches.joinToString { it.originalMethod.toString() },
                )
            }
            patchAudioTrackOverride(audioTrackMatches.single(), forceHighestQualitySetting)

            val bitrateLimiterMatches = MediaBitrateLimiterFingerprint.scopedMatchAll()
            if (bitrateLimiterMatches.size != 1) {
                throw PatchException(
                    "Expected exactly one MediaBitrateLimiter match, found ${bitrateLimiterMatches.size}: " +
                        bitrateLimiterMatches.joinToString { it.originalMethod.toString() },
                )
            }
            patchBitrateLimiter(bitrateLimiterMatches.single(), forceHighestQualitySetting)

            val betaVideoQualityMatches =
                BetaVideoQualityFingerprint.scopedMatchAllOrNull().orEmpty()
            val alphaVideoQualityMatches =
                AlphaVideoQualityFingerprint.scopedMatchAllOrNull().orEmpty()
            val videoQualityMatches = betaVideoQualityMatches + alphaVideoQualityMatches
            if (videoQualityMatches.size != 1) {
                throw PatchException(
                    "Expected exactly one NewX video quality target across beta/alpha paths, " +
                        "found beta=${betaVideoQualityMatches.size}, " +
                        "alpha=${alphaVideoQualityMatches.size}: " +
                        videoQualityMatches.joinToString { it.originalMethod.toString() },
                )
            }

            if (betaVideoQualityMatches.size == 1) {
                patchBetaVideoQuality(betaVideoQualityMatches.single(), forceHighestQualitySetting)
            } else {
                patchAlphaVideoQuality(alphaVideoQualityMatches.single(), forceHighestQualitySetting)
            }
        }
    }

private data class ViewportClampTarget(
    val entryIndex: Int,
    val maximumInstruction: Instruction,
)

private fun resolveViewportClamp(
    instructions: List<Instruction>,
    definingClass: String,
): ViewportClampTarget {
    val viewportWidthIndex =
        findNextFieldWriteIndex(instructions, 0, definingClass, "f")
    if (viewportWidthIndex == -1) {
        throw PatchException("Could not find the Viewport width re-clamp write")
    }

    val viewportHeightIndex =
        findNextFieldWriteIndex(instructions, viewportWidthIndex + 1, definingClass, "g")
    if (viewportHeightIndex == -1) {
        throw PatchException("Could not find the Viewport height re-clamp write")
    }

    val reclampBranchIndex =
        (0 until viewportWidthIndex).lastOrNull { index ->
            instructions[index].opcode == Opcode.IF_EQZ &&
                hasViewportReclampShape(instructions, index + 1, viewportWidthIndex)
        } ?: throw PatchException("Could not find the Viewport re-clamp branch")
    val entryIndex =
        (0 until reclampBranchIndex).lastOrNull { index ->
            instructions[index].opcode == Opcode.INSTANCE_OF
        } ?: throw PatchException("Could not find the Viewport re-clamp entry")

    val gotoIndex =
        findNextInstructionIndex(instructions, viewportHeightIndex + 1, ::isGoto)
    if (gotoIndex == -1) {
        throw PatchException("Could not find the Viewport re-clamp continuation")
    }

    val maximumIndex = gotoIndex + 1
    val maximumInstruction =
        instructions.getOrNull(maximumIndex)
            ?: throw PatchException("Viewport re-clamp continuation has no maximum branch")
    val maximumHeightIndex =
        findNextFieldWriteIndex(instructions, maximumIndex, definingClass, "g")
    val maximumWidthIndex =
        findNextFieldWriteIndex(instructions, maximumHeightIndex + 1, definingClass, "f")
    if (maximumHeightIndex == -1 || maximumWidthIndex == -1) {
        throw PatchException("Could not resolve the Viewport maximum fallback writes")
    }

    return ViewportClampTarget(
        entryIndex = entryIndex,
        maximumInstruction = maximumInstruction,
    )
}

private fun isGoto(instruction: Instruction): Boolean =
    instruction.opcode == Opcode.GOTO ||
        instruction.opcode == Opcode.GOTO_16 ||
        instruction.opcode == Opcode.GOTO_32

private fun patchViewportClamp(
    method: MutableMethod,
    setting: ToggleSettingDefinition,
    target: ViewportClampTarget,
    settingRegister: Int,
    labelPrefix: String,
) {
    // Self-contained guard: the read must sit immediately before its own branch.
    // Reusing a register defined at another injection site is unsound — original
    // branches can jump past that site, leaving the register undefined (VerifyError).
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = target.entryIndex,
            defaultValue = true,
            registerRange = settingRegister..(settingRegister + 1),
        )
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-nez v${read.register}, :${labelPrefix}_maximum
        """.trimIndent(),
        ExternalLabel("${labelPrefix}_maximum", target.maximumInstruction),
    )
}

private fun addConditionalMaximum(
    method: MutableMethod,
    setting: ToggleSettingDefinition,
    index: Int,
    targetInstruction: Instruction,
    settingRegister: Int,
    valueRegister: Int,
    label: String,
) {
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = index,
            defaultValue = true,
            registerRange = settingRegister..(settingRegister + 1),
        )
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-eqz v${read.register}, :$label
        const v$valueRegister, 0x7fffffff
        """.trimIndent(),
        ExternalLabel(label, targetInstruction),
    )
}

private fun injectReadAndConditionalMaximum(
    method: MutableMethod,
    setting: ToggleSettingDefinition,
    index: Int,
    targetInstruction: Instruction,
    settingRegister: Int,
    valueRegister: Int,
    label: String,
) {
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = index,
            defaultValue = true,
            registerRange = settingRegister..(settingRegister + 1),
        )
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-eqz v${read.register}, :$label
        const v$valueRegister, 0x7fffffff
        """.trimIndent(),
        ExternalLabel(label, targetInstruction),
    )
}

private fun resolveIntegerArgumentRegister(instruction: Instruction): Int {
    val fiveRegisterInstruction = instruction as? FiveRegisterInstruction
    if (fiveRegisterInstruction != null) {
        if (fiveRegisterInstruction.registerCount != 2) {
            throw PatchException("Expected a two-register TrackSelectionParameters call")
        }
        return fiveRegisterInstruction.registerD
    }

    val rangeInstruction = instruction as? RegisterRangeInstruction
    if (rangeInstruction != null) {
        if (rangeInstruction.registerCount != 2) {
            throw PatchException("Expected a two-register TrackSelectionParameters range call")
        }
        return rangeInstruction.startRegister + 1
    }

    throw PatchException("Could not resolve the TrackSelectionParameters integer argument register")
}

private fun resolveFieldValueRegister(instruction: Instruction): Int =
    (instruction as? TwoRegisterInstruction)?.registerA
        ?: throw PatchException("Could not resolve the TrackSelectionParameters field value register")

context(context: BytecodePatchContext)
private fun patchBetaVideoQuality(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("Beta video quality method has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()
    val gCallIndices =
        instructionsList.indices.filter { index ->
            isTrackSelectionBuilderCall(instructionsList[index], "g")
        }
    val fCallIndices =
        instructionsList.indices.filter { index ->
            isTrackSelectionBuilderCall(instructionsList[index], "f")
        }
    if (gCallIndices.size != 1 || fCallIndices.size != 1) {
        throw PatchException(
            "Expected one paired beta TrackSelectionParameters g/f call, " +
                "found g=${gCallIndices.size}, f=${fCallIndices.size}",
        )
    }

    val gCallIndex = gCallIndices.single()
    val fCallIndex = fCallIndices.single()
    if (gCallIndex >= fCallIndex) {
        throw PatchException("Beta TrackSelectionParameters g/f calls are not ordered")
    }

    val gInstruction = instructionsList[gCallIndex]
    val fInstruction = instructionsList[fCallIndex]
    val builderClass = trackSelectionBuilderClass(gInstruction)
        ?: throw PatchException("Could not resolve the beta TrackSelectionParameters builder class")
    if (trackSelectionBuilderClass(fInstruction) != builderClass) {
        throw PatchException("Beta TrackSelectionParameters g/f calls target different builders")
    }
    val gValueRegister = resolveIntegerArgumentRegister(gInstruction)
    val fValueRegister = resolveIntegerArgumentRegister(fInstruction)
    val viewportClamp = resolveViewportClamp(instructionsList, builderClass)
    val settingRegister = originalRegisterCount

    // Apply edits from the end of the method toward the first call so original indices remain valid.
    patchViewportClamp(
        method = method,
        setting = setting,
        target = viewportClamp,
        settingRegister = settingRegister,
        labelPrefix = "piko_newx_force_hq_beta_viewport",
    )
    addConditionalMaximum(
        method = method,
        setting = setting,
        index = fCallIndex,
        targetInstruction = fInstruction,
        settingRegister = settingRegister,
        valueRegister = fValueRegister,
        label = "piko_newx_force_hq_beta_f",
    )
    injectReadAndConditionalMaximum(
        method = method,
        setting = setting,
        index = gCallIndex,
        targetInstruction = gInstruction,
        settingRegister = settingRegister,
        valueRegister = gValueRegister,
        label = "piko_newx_force_hq_beta_g",
    )
}

context(context: BytecodePatchContext)
private fun patchAlphaVideoQuality(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("Alpha video quality method has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()
    val eWriteIndices =
        instructionsList.indices.filter { index ->
            isTrackSelectionFieldWrite(instructionsList[index], "e")
        }
    val widthWriteIndices =
        instructionsList.indices.filter { index ->
            isTrackSelectionFieldWrite(instructionsList[index], "a")
        }
    val heightWriteIndices =
        instructionsList.indices.filter { index ->
            isTrackSelectionFieldWrite(instructionsList[index], "b")
        }
    if (eWriteIndices.size != 1 || widthWriteIndices.size != 1 || heightWriteIndices.size != 1) {
        throw PatchException(
            "Expected one alpha TrackSelectionParameters a/b/e write, " +
                "found a=${widthWriteIndices.size}, b=${heightWriteIndices.size}, " +
                "e=${eWriteIndices.size}",
        )
    }

    val eWriteIndex = eWriteIndices.single()
    val widthWriteIndex = widthWriteIndices.single()
    val heightWriteIndex = heightWriteIndices.single()
    if (!(eWriteIndex < widthWriteIndex && widthWriteIndex < heightWriteIndex)) {
        throw PatchException("Alpha TrackSelectionParameters a/b/e writes are not ordered")
    }

    val eInstruction = instructionsList[eWriteIndex]
    val widthInstruction = instructionsList[widthWriteIndex]
    val heightInstruction = instructionsList[heightWriteIndex]
    val builderClass = fieldWriteBuilderClass(eInstruction, "e")
        ?: throw PatchException("Could not resolve the alpha TrackSelectionParameters builder class")
    if (fieldWriteBuilderClass(widthInstruction, "a") != builderClass ||
        fieldWriteBuilderClass(heightInstruction, "b") != builderClass
    ) {
        throw PatchException("Alpha TrackSelectionParameters a/b/e writes target different builders")
    }
    val eValueRegister = resolveFieldValueRegister(eInstruction)
    val widthValueRegister = resolveFieldValueRegister(widthInstruction)
    val heightValueRegister = resolveFieldValueRegister(heightInstruction)
    val viewportClamp = resolveViewportClamp(instructionsList, builderClass)
    val settingRegister = originalRegisterCount

    // Apply edits from the end of the method toward the first field write so original indices remain valid.
    patchViewportClamp(
        method = method,
        setting = setting,
        target = viewportClamp,
        settingRegister = settingRegister,
        labelPrefix = "piko_newx_force_hq_alpha_viewport",
    )
    addConditionalMaximum(
        method = method,
        setting = setting,
        index = heightWriteIndex,
        targetInstruction = heightInstruction,
        settingRegister = settingRegister,
        valueRegister = heightValueRegister,
        label = "piko_newx_force_hq_alpha_height",
    )
    addConditionalMaximum(
        method = method,
        setting = setting,
        index = widthWriteIndex,
        targetInstruction = widthInstruction,
        settingRegister = settingRegister,
        valueRegister = widthValueRegister,
        label = "piko_newx_force_hq_alpha_width",
    )
    injectReadAndConditionalMaximum(
        method = method,
        setting = setting,
        index = eWriteIndex,
        targetInstruction = eInstruction,
        settingRegister = settingRegister,
        valueRegister = eValueRegister,
        label = "piko_newx_force_hq_alpha_e",
    )
}

context(context: BytecodePatchContext)
private fun patchAudioTrackOverride(
    match: Match,
    setting: app.crimera.patches.newx.settings.ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount = originalMethod.implementation?.registerCount
        ?: throw PatchException("onTracksChanged has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()

    // 1. Find string and regex instruction positions on the cloned method
    val skipOverrideStringIndex = instructionsList.indexOfFirst { instruction ->
        instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string == "Audio bitrates are known, skipping override"
    }
    if (skipOverrideStringIndex == -1) {
        throw PatchException("Could not find skip override string in onTracksChanged")
    }

    val regexStringIndex = instructionsList.indexOfFirst { instruction ->
        instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string == "audio-(\\d+)"
    }
    if (regexStringIndex == -1) {
        throw PatchException("Could not find audio-(\\d+) string in onTracksChanged")
    }

    // 2. Resolve the Format.bitrate field read (the `iget vX, vY, Format->j:I` right before the skip-override string)
    val bitrateFieldReadIndex = instructionsList.take(skipOverrideStringIndex).indexOfLast { instruction ->
        instruction.opcode == Opcode.IGET &&
            instruction.getReference<FieldReference>()?.type == "I"
    }
    if (bitrateFieldReadIndex == -1) {
        throw PatchException("Could not resolve the Format.bitrate field read in onTracksChanged")
    }

    // 3. Find the early-return branch before skipOverrideStringIndex
    val branchInstructionIndex = (bitrateFieldReadIndex + 1 until skipOverrideStringIndex)
        .firstOrNull { index ->
            instructionsList[index].opcode in listOf(
                Opcode.IF_NE,
                Opcode.IF_EQ,
                Opcode.IF_NEZ,
                Opcode.IF_EQZ,
            )
        } ?: throw PatchException("Could not find branch instruction guarding audio bitrates override")

    val newInstanceRegexIndex = (0 until regexStringIndex).lastOrNull { index ->
        instructionsList[index].opcode == Opcode.NEW_INSTANCE &&
            instructionsList[index].getReference<com.android.tools.smali.dexlib2.iface.reference.TypeReference>()?.type == "Lkotlin/text/Regex;"
    } ?: (regexStringIndex - 1)

    val regexInstruction = instructionsList[newInstanceRegexIndex]

    // Guard the early-branch so that if setting is enabled, it jumps straight to the regex/override logic
    val settingRegister = originalRegisterCount
    val defaultRegister = settingRegister + 1
    val read = setting.injectReadWithDefault(
        method = method,
        index = branchInstructionIndex,
        defaultValue = true,
        registerRange = settingRegister..defaultRegister,
    )
    val label = "piko_audio_quality_check_${branchInstructionIndex}"
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-nez v${read.register}, :$label
        goto :piko_force_audio_override
        """.trimIndent(),
        ExternalLabel(label, instructionsList[branchInstructionIndex]),
        ExternalLabel("piko_force_audio_override", regexInstruction),
    )
}

context(context: BytecodePatchContext)
private fun patchBitrateLimiter(
    match: Match,
    setting: app.crimera.patches.newx.settings.ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount = originalMethod.implementation?.registerCount
        ?: throw PatchException("Bitrate limiter method has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()

    val mathMinIndex = instructionsList.indexOfFirst { instruction ->
        instruction.opcode in listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.MethodReference>()?.let {
                it.definingClass == "Ljava/lang/Math;" && it.name == "min"
            } == true
    }
    if (mathMinIndex == -1) {
        throw PatchException("Could not find Math.min call in bitrate limiter")
    }

    val moveResultIndex = mathMinIndex + 1
    val moveResult = instructionsList.getOrNull(moveResultIndex) as? OneRegisterInstruction
        ?: throw PatchException("Math.min is not followed by move-result")

    val resultRegister = moveResult.registerA
    val settingRegister = originalRegisterCount
    val defaultRegister = settingRegister + 1
    val read = setting.injectReadWithDefault(
        method = method,
        index = moveResultIndex + 1,
        defaultValue = true,
        registerRange = settingRegister..defaultRegister,
    )
    val label = "piko_bitrate_limiter_skip"
    val nextInstruction = instructionsList.getOrNull(moveResultIndex + 1)
        ?: throw PatchException("No instruction after move-result in bitrate limiter")

    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-eqz v${read.register}, :$label
        const v$resultRegister, 0x7fffffff
        """.trimIndent(),
        ExternalLabel(label, nextInstruction),
    )
}
