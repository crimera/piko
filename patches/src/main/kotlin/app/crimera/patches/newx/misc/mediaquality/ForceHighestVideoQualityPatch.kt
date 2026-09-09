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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val MAXIMUM_VIDEO_BITRATE = 0x7fffffff
private const val REGEX_DESCRIPTOR = "Lkotlin/text/Regex;"
private const val INTEGER_DESCRIPTOR = "I"

private data class AudioBitrateCandidate(
    val fieldIndex: Int,
    val field: FieldReference,
    val branchIndex: Int,
)

private data class RegexAllocationCandidate(
    val allocationIndex: Int,
    val patternIndex: Int,
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
    // The telemetry owner is an obfuscated class and changes independently of
    // the package. Keep the stable package scope and the coroutine method name.
    definingClass = "Lcom/x/media/imageloader/telemetry/",
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
    custom = { method, _ ->
        method.name == "invokeSuspend" &&
            method.implementation?.instructions?.toList()?.let { instructions ->
                instructions.indices.any { index ->
                    isBitrateFlowRead(instructions[index]) &&
                        instructions.getOrNull(index + 1)?.let(::isIntegerMathMin) == true
                }
            } == true
    },
)

/**
 * Newer builds removed the telemetry coroutine that clamped the bitrate. The
 * playback policy now returns Integer.MAX_VALUE directly, so the requested
 * behavior is already present and there is no value to override.
 */
private object MaxBitratePolicyFingerprint : Fingerprint(
    definingClass = "Lcom/x/media/playback/",
    returnType = "I",
    parameters = listOf("J"),
    filters = listOf(
        literal(MAXIMUM_VIDEO_BITRATE),
        opcode(Opcode.RETURN, MatchAfterImmediately()),
    ),
)

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

            val bitrateLimiterMatches = MediaBitrateLimiterFingerprint.scopedMatchAllOrNull().orEmpty()
            when {
                bitrateLimiterMatches.size == 1 ->
                    patchBitrateLimiter(bitrateLimiterMatches.single(), forceHighestQualitySetting)
                bitrateLimiterMatches.size > 1 ->
                    throw PatchException(
                        "Expected at most one MediaBitrateLimiter match, found ${bitrateLimiterMatches.size}: " +
                            bitrateLimiterMatches.joinToString { it.originalMethod.toString() },
                    )
                else -> {
                    // Older targets also contain this already-unlimited policy
                    // alongside the telemetry limiter, so only use it as a
                    // fallback after the telemetry shape is absent.
                    val maxBitratePolicyMatches = MaxBitratePolicyFingerprint.scopedMatchAllOrNull().orEmpty()
                    if (maxBitratePolicyMatches.size != 1) {
                        throw PatchException(
                            "Expected one media bitrate capability across telemetry or max-policy shapes, " +
                                "found telemetry=0, maxPolicy=${maxBitratePolicyMatches.size}: " +
                                maxBitratePolicyMatches.joinToString { it.originalMethod.toString() },
                        )
                    }
            }
        }
    }
}

private fun isBitrateFlowRead(instruction: Instruction?): Boolean {
    if (instruction?.opcode != Opcode.IGET_OBJECT) return false

    val reference = instruction.getReference<FieldReference>() ?: return false
    return reference.type.startsWith("Lkotlinx/coroutines/flow/")
}

private fun isStringConstant(instruction: Instruction?, value: String): Boolean {
    if (instruction?.opcode != Opcode.CONST_STRING &&
        instruction?.opcode != Opcode.CONST_STRING_JUMBO
    ) {
        return false
    }
    return instruction.getReference<StringReference>()?.string == value
}

private fun isRegexConstructor(
    instruction: Instruction?,
    regexRegister: Int,
    patternRegister: Int,
): Boolean {
    if (instruction?.opcode != Opcode.INVOKE_DIRECT &&
        instruction?.opcode != Opcode.INVOKE_DIRECT_RANGE
    ) {
        return false
    }
    val reference = instruction.getReference<MethodReference>() ?: return false
    return reference.definingClass == REGEX_DESCRIPTOR &&
        reference.name == "<init>" &&
        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") &&
        reference.returnType == "V" &&
        instruction.registersUsed == listOf(regexRegister, patternRegister)
}

private fun isIntegerMathMin(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_STATIC &&
        instruction.opcode != Opcode.INVOKE_STATIC_RANGE
    ) {
        return false
    }

    val reference = instruction.getReference<MethodReference>() ?: return false
    return reference.definingClass == "Ljava/lang/Math;" &&
        reference.name == "min" &&
        reference.parameterTypes == listOf("I", "I") &&
        reference.returnType == "I"
}

context(context: BytecodePatchContext)
private fun patchAudioTrackOverride(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("onTracksChanged has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()
    val skipOverrideStringCandidates =
        instructionsList.withIndex().filter { (_, instruction) ->
            isStringConstant(instruction, "Audio bitrates are known, skipping override")
        }
    if (skipOverrideStringCandidates.size != 1) {
        throw PatchException(
            "Expected one skip-override marker in onTracksChanged, found " +
                "${skipOverrideStringCandidates.size}: " +
                "${skipOverrideStringCandidates.joinToString { "${it.index}:${it.value}" }}",
        )
    }
    val skipOverrideStringIndex = skipOverrideStringCandidates.single().index

    val regexStringCandidates =
        instructionsList.withIndex().filter { (_, instruction) ->
            isStringConstant(instruction, "audio-(\\d+)")
        }
    if (regexStringCandidates.size != 1) {
        throw PatchException(
            "Expected one audio bitrate Regex marker in onTracksChanged, found " +
                "${regexStringCandidates.size}: " +
                "${regexStringCandidates.joinToString { "${it.index}:${it.value}" }}",
        )
    }
    val regexStringIndex = regexStringCandidates.single().index

    val bitrateFieldCandidates =
        instructionsList.withIndex().mapNotNull { (index, instruction) ->
            if (index >= skipOverrideStringIndex || instruction.opcode != Opcode.IGET) {
                return@mapNotNull null
            }
            val fieldInstruction = instruction as? TwoRegisterInstruction
                ?: return@mapNotNull null
            val field = instruction.getReference<FieldReference>()
                ?: return@mapNotNull null
            if (field.type.toString() != INTEGER_DESCRIPTOR) return@mapNotNull null

            val resultInstruction =
                instructionsList.getOrNull(index - 1) as? OneRegisterInstruction
                    ?: return@mapNotNull null
            val getterInstruction = instructionsList.getOrNull(index - 2)
                ?: return@mapNotNull null
            val getter = getterInstruction.getReference<MethodReference>()
                ?: return@mapNotNull null
            val branchInstruction = instructionsList.getOrNull(index + 1)
                ?: return@mapNotNull null
            if (getterInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT ||
                resultInstruction.registerA != fieldInstruction.registerB ||
                getter.returnType.toString() != field.definingClass.toString() ||
                getter.parameterTypes.map(CharSequence::toString) != listOf(INTEGER_DESCRIPTOR) ||
                branchInstruction.opcode !in setOf(
                    Opcode.IF_NE,
                    Opcode.IF_EQ,
                    Opcode.IF_NEZ,
                    Opcode.IF_EQZ,
                ) ||
                fieldInstruction.registerA !in branchInstruction.registersUsed
            ) {
                return@mapNotNull null
            }
            AudioBitrateCandidate(index, field, index + 1)
        }
    if (bitrateFieldCandidates.size != 1) {
        throw PatchException(
            "Expected one semantic Format.bitrate read before the skip-override marker, found " +
                "${bitrateFieldCandidates.size}: " +
                "${bitrateFieldCandidates.joinToString { "${it.fieldIndex}:${it.field}" }}",
        )
    }
    val bitrateFieldCandidate = bitrateFieldCandidates.single()
    val bitrateFieldReadIndex = bitrateFieldCandidate.fieldIndex

    val branchInstructionIndex = bitrateFieldCandidate.branchIndex

    val regexAllocationCandidates =
        instructionsList.withIndex().mapNotNull { (index, instruction) ->
            if (index >= regexStringIndex || instruction.opcode != Opcode.NEW_INSTANCE) {
                return@mapNotNull null
            }
            val allocationRegister =
                (instruction as? OneRegisterInstruction)?.registerA
                    ?: return@mapNotNull null
            val type = instruction
                .getReference<com.android.tools.smali.dexlib2.iface.reference.TypeReference>()
                ?.type
                ?: return@mapNotNull null
            if (type != REGEX_DESCRIPTOR) return@mapNotNull null
            val patternInstruction =
                instructionsList.getOrNull(index + 1) as? OneRegisterInstruction
                    ?: return@mapNotNull null
            if (!isStringConstant(instructionsList.getOrNull(index + 1), "audio-(\\d+)")) {
                return@mapNotNull null
            }
            if (!isRegexConstructor(
                    instructionsList.getOrNull(index + 2),
                    allocationRegister,
                    patternInstruction.registerA,
                )
            ) {
                return@mapNotNull null
            }
            RegexAllocationCandidate(index, index + 1)
        }
    if (regexAllocationCandidates.size != 1) {
        throw PatchException(
            "Expected one audio bitrate Regex allocation for marker @${regexStringIndex}, found " +
                "${regexAllocationCandidates.size}: " +
                "${regexAllocationCandidates.joinToString { "${it.allocationIndex}:${it.patternIndex}" }}",
        )
    }
    val regexAllocationCandidate = regexAllocationCandidates.single()
    if (regexAllocationCandidate.patternIndex != regexStringIndex) {
        throw PatchException(
            "Audio bitrate Regex allocation does not consume the unique marker at " +
                "${regexStringIndex}: ${regexAllocationCandidate}",
        )
    }
    val newInstanceRegexIndex = regexAllocationCandidate.allocationIndex

    val regexInstruction = instructionsList[newInstanceRegexIndex]
    val settingRegister = originalRegisterCount
    val defaultRegister = settingRegister + 1
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = branchInstructionIndex,
            defaultValue = true,
            registerRange = settingRegister..defaultRegister,
        )
    val label = "piko_audio_quality_check_$branchInstructionIndex"
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
        if-eqz v${read.register}, :$label
        goto :piko_force_audio_override
        """.trimIndent(),
        ExternalLabel(label, instructionsList[branchInstructionIndex]),
        ExternalLabel("piko_force_audio_override", regexInstruction),
    )
}

context(context: BytecodePatchContext)
private fun patchBitrateLimiter(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val ownerClass = context.mutableClassDefBy(match.originalClassDef.type)
    val originalMethod = match.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("Bitrate limiter method has no implementation")
    val method = originalMethod.cloneMutable(additionalRegisters = 4)
    ownerClass.methods.remove(originalMethod)
    ownerClass.methods.add(method)

    val instructionsList = method.instructions.toList()
    val mathMinCandidates =
        instructionsList.withIndex().filter { (index, instruction) ->
            isIntegerMathMin(instruction) &&
                isBitrateFlowRead(instructionsList.getOrNull(index - 1))
        }
    if (mathMinCandidates.size != 1) {
        throw PatchException(
            "Expected one Math.min call updating the media bitrate limit, found " +
                "${mathMinCandidates.size}: ${mathMinCandidates.joinToString { "${it.index}:${it.value}" }}",
        )
    }
    val mathMinIndex = mathMinCandidates.single().index

    val moveResultIndex = mathMinIndex + 1
    val moveResult = instructionsList.getOrNull(moveResultIndex) as? OneRegisterInstruction
        ?: throw PatchException("Math.min is not followed by move-result")
    if (moveResult.opcode != Opcode.MOVE_RESULT) {
        throw PatchException("Math.min is not followed by move-result: ${instructionsList[moveResultIndex]}")
    }

    val resultRegister = moveResult.registerA
    val settingRegister = originalRegisterCount
    val defaultRegister = settingRegister + 1
    val read =
        setting.injectReadWithDefault(
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
        const v$resultRegister, 0x${MAXIMUM_VIDEO_BITRATE.toString(16)}
        """.trimIndent(),
        ExternalLabel(label, nextInstruction),
    )
}
