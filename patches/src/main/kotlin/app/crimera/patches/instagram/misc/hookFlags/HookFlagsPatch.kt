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
import com.android.tools.smali.dexlib2.AccessFlags

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
                        }.apply {
                            // This is needed in order to find the profile info parameter.
                            val isStaticMethod = AccessFlags.STATIC.isSet(method.accessFlags)

                            var configSpecifierRegister = parameters.indexOfFirst { it.type == "J" }
                            if (!isStaticMethod) {
                                configSpecifierRegister += 1
                            }
                            addInstructions(
                                0,
                                """
                                invoke-static/range {p$configSpecifierRegister .. p${configSpecifierRegister + 1}}, $HOOK_FLAGS_DESCRIPTOR->handleBoolFlags(J)Ljava/lang/Boolean;
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
                        }
                }
            }
        }
    }
