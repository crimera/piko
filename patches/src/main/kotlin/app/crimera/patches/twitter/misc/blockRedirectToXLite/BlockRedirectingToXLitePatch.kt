/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.blockRedirectToXLite

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.is_11_98_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import java.util.logging.Logger

private object RedirectingToXLiteFlagCheckFingerprint : Fingerprint(
    returnType = "Z",
    strings =
        listOf(
            "x_lite_in_tfa_for_existing_users_enabled",
            "existing_user_redirected_to_x_lite",
            "x_lite_in_tfa_for_existing_users_exit_enabled",
        ),
)

private object XLiteSettingItemsAdderFingerprint : Fingerprint(
    filters =
        listOf(
            resourceLiteral(ResourceType.STRING, "settings_back_to_x_item_title"),
        ),
)

@Suppress("unused")
val blockRedirectingToXLitePatch =
    bytecodePatch(
        name = "Block redirecting to X Lite",
        description = "Blocks redirecting to the new X Android UI on launch",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, versionCheckPatch, resourceMappingPatch)

        execute {

            if (is_11_98_or_greater) {
                RedirectingToXLiteFlagCheckFingerprint.method.apply {
                    val lastMoveResultObjectInstruction = instructions.last { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    val lastMoveResultObjectIndex = lastMoveResultObjectInstruction.location.index

                    val putBooleanIntoSharedPrefIndex = lastMoveResultObjectIndex + 1
                    val putBooleanIntoSharedPrefInstruction = getInstruction(putBooleanIntoSharedPrefIndex)
                    val boolRegister = putBooleanIntoSharedPrefInstruction.registersUsed[2]

                    addInstruction(
                        putBooleanIntoSharedPrefIndex,
                        """
                        const v$boolRegister, 0x0
                        """.trimIndent(),
                    )
                }

                XLiteSettingItemsAdderFingerprint.apply {
                    val strIndex = instructionMatches.first().index
                    method.apply {
                        val originalXItemCheckInstruction =
                            instructions.last { it.opcode == Opcode.IF_EQZ && it.location.index < strIndex }
                        val register = originalXItemCheckInstruction.registersUsed[0]
                        addInstruction(originalXItemCheckInstruction.location.index, "const v$register, 0x1")
                    }
                }

                enableSettings("blockRedirectingToXLite")
            } else {
                return@execute Logger.getLogger(this::class.java.name).warning(
                    "The patch \"Block redirecting to X Lite\" is force succeeded and does not work on any version below 11.98.\n",
                )
            }
        }
    }
