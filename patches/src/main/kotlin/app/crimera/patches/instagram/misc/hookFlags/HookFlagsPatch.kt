/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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

@Suppress("unused")
val hookFlagsPatch =
    bytecodePatch(
        description = "Hooks flag check to override default flag value",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StringFlagCheckMethodFingerprint.apply {
                val methods = classDef.methods

                method.apply {
                    val configValueSourceWrapperClassType = parameters[0].type

                    // Boolean flag check method.
                    methods
                        .first {
                            it.returnType == "Z" && it.parameters.size == 3 &&
                                it.parameters[0].type == configValueSourceWrapperClassType
                        }.addInstructions(
                            0,
                            """
                            invoke-static {p2, p3}, $HOOK_FLAGS_DESCRIPTOR->handleBoolFlags(J)Ljava/lang/Boolean;
                            move-result-object v0
                            
                            # 1. Check if the result is NULL
                            if-eqz v0, :piko
                            
                            invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
                            move-result v1
                            return-object v1
                            :piko
                            nop
                            """.trimIndent(),
                        )
                }
            }
        }
    }
