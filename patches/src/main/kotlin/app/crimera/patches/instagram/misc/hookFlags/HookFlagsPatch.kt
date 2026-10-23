/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.hookFlags

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.HOOK_FLAGS_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

internal object StringFlagCheckMethodFingerprint : Fingerprint(
    strings = listOf("__fbt_null__"),
    returnType = "Ljava/lang/String;",
)

// Flag checks of a config value source with a config specifier.
internal object BooleanFlagCheckMethodFingerprint : Fingerprint(
    classFingerprint = StringFlagCheckMethodFingerprint,
    returnType = "Z",
    parameters = listOf("L", "J", "Z"),
)

internal object LongFlagCheckMethodFingerprint : Fingerprint(
    classFingerprint = StringFlagCheckMethodFingerprint,
    returnType = "J",
    parameters = listOf("L", "J", "J"),
)

@Suppress("unused")
val hookFlagsPatch =
    bytecodePatch(
        description = "Hooks flag check to override default flag value",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // The config specifier is the second parameter.
            BooleanFlagCheckMethodFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range {p2 .. p3}, $HOOK_FLAGS_DESCRIPTOR->handleBoolFlags(J)Ljava/lang/Boolean;
                move-result-object v0
                # 1. Check if the result is NULL
                if-eqz v0, :piko
                invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
                move-result v1
                return v1
                :piko
                nop
                """.trimIndent(),
            )

            LongFlagCheckMethodFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range {p2 .. p3}, $HOOK_FLAGS_DESCRIPTOR->handleLongFlags(J)Ljava/lang/Long;
                move-result-object v0
                # 1. Check if the result is NULL
                if-eqz v0, :piko
                invoke-virtual {v0}, Ljava/lang/Long;->longValue()J
                move-result-wide v1
                return-wide v1
                :piko
                nop
                """.trimIndent(),
            )
        }
    }
