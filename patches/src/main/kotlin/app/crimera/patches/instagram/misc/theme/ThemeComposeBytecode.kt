/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.common.declaredParameterRegister
import app.crimera.patches.common.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

context(patchContext: BytecodePatchContext)
internal fun installComposeNativeThemeModeSync() {
    DarkModeSectionFingerprint.let {
        val method = it.method
        val result = it.instructionMatches.last()
        val resultRegister = result.getInstruction<OneRegisterInstruction>().registerA
        if (resultRegister !in 0..0xf) {
            throw PatchException("Compose native theme setting read requires a 4-bit register")
        }
        method.addInstruction(
            result.index + 1,
            "invoke-static {v$resultRegister}, " +
                "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
                "observeComposeNativeThemeMode(I)V",
        )

        // The section is static, so the callback is the register after the composer.
        val callbackRegister = method.p0Register + 1
        if (callbackRegister !in 0..0xf) {
            throw PatchException("Compose native theme callback requires a 4-bit register")
        }

        method.addInstructions(
            0,
            """
            invoke-static {v$callbackRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->wrapNativeThemeCallback(Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;
            move-result-object v$callbackRegister
            """.trimIndent(),
        )
    }
}
