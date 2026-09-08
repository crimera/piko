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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MAXIMUM_VIDEO_BITRATE = 0x7fffffff

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
    val skipOverrideStringIndex = instructionsList.indexOfFirst { instruction ->
        instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string ==
            "Audio bitrates are known, skipping override"
    }
    if (skipOverrideStringIndex == -1) {
        throw PatchException("Could not find skip override string in onTracksChanged")
    }

    val regexStringIndex = instructionsList.indexOfFirst { instruction ->
        instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string ==
            "audio-(\\d+)"
    }
    if (regexStringIndex == -1) {
        throw PatchException("Could not find audio-(\\d+) string in onTracksChanged")
    }

    val bitrateFieldReadIndex = instructionsList.take(skipOverrideStringIndex).indexOfLast { instruction ->
        instruction.opcode == Opcode.IGET &&
            instruction.getReference<FieldReference>()?.type == "I"
    }
    if (bitrateFieldReadIndex == -1) {
        throw PatchException("Could not resolve the Format.bitrate field read in onTracksChanged")
    }

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
            instructionsList[index].getReference<com.android.tools.smali.dexlib2.iface.reference.TypeReference>()?.type ==
            "Lkotlin/text/Regex;"
    } ?: (regexStringIndex - 1)

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
    val mathMinIndex =
        instructionsList.indices.firstOrNull { index ->
            isIntegerMathMin(instructionsList[index]) &&
                isBitrateFlowRead(instructionsList.getOrNull(index - 1))
        } ?: -1
    if (mathMinIndex == -1) {
        throw PatchException("Could not find Math.min call updating the media bitrate limit")
    }

    val moveResultIndex = mathMinIndex + 1
    val moveResult = instructionsList.getOrNull(moveResultIndex) as? OneRegisterInstruction
        ?: throw PatchException("Math.min is not followed by move-result")

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
