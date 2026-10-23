/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.common.parameterRegisterStart
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.IgFragmentActivityOnCreate
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
internal const val STRING_DESCRIPTOR = "Ljava/lang/String;"
internal const val LIST_DESCRIPTOR = "Ljava/util/List;"
internal const val COLLECTION_DESCRIPTOR = "Ljava/util/Collection;"
internal const val RADIO_GROUP_LISTENER_DESCRIPTOR =
    "Landroid/widget/RadioGroup\$OnCheckedChangeListener;"

internal fun legacyNativeThemeListenerInvocation(
    listenerRegister: Int,
    packedIdsRegister: Int,
): String =
    "invoke-static {v$listenerRegister, v$packedIdsRegister}, " +
        "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
        "wrapLegacyNativeThemeListener(" +
        "Landroid/widget/RadioGroup\$OnCheckedChangeListener;I)" +
        "Landroid/widget/RadioGroup\$OnCheckedChangeListener;"

internal fun systemUiModeCacheResolutionInvocation(
    resultRegister: Int,
): String =
    "invoke-static {v$resultRegister}, " +
        "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
        "resolveSystemUiModeCache(I)I"

context(patchContext: BytecodePatchContext)
internal fun installThemeLifecycleHooks() {
    instagramInitHook.fingerprint.method.apply {
        val invokeSuperIndex = indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER)
        val contextRegister = getInstruction(invokeSuperIndex).registersUsed.first()

        addInstruction(
            invokeSuperIndex + 1,
            """
            invoke-static {v$contextRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->initialize(Landroid/content/Context;)V
            """.trimIndent(),
        )
    }

    IgFragmentActivityOnCreate.method.addInstruction(
        0,
        """
        invoke-static {p0}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->applyToActivity(Landroid/app/Activity;)V
        """.trimIndent(),
    )
}

context(patchContext: BytecodePatchContext)
internal fun installSystemDefaultUiModeHook() {
    CurrentSystemUiModeFingerprint.let {
        val returnMatch = it.instructionMatches.last()
        val resultRegister = returnMatch.getInstruction<OneRegisterInstruction>().registerA
        if (resultRegister !in 0..0xf) {
            throw PatchException(
                "Current system ui mode result register is not 4-bit: v$resultRegister",
            )
        }

        it.method.addInstructions(
            returnMatch.index,
            """
            ${systemUiModeCacheResolutionInvocation(resultRegister)}
            move-result v$resultRegister
            """.trimIndent(),
        )
    }
}

context(patchContext: BytecodePatchContext)
internal fun installNativeThemeModeSync() {
    installComposeNativeThemeModeSync()
    installLegacyNativeThemeModeSync()
}
