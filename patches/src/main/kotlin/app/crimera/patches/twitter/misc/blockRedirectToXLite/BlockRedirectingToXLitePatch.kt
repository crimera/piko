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
import app.crimera.patches.twitter.utils.is_12_07_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import java.util.logging.Logger

private object RedirectingToXLiteFlagCheckFingerprint : Fingerprint(
    returnType = "Z",
    filters =
        listOf(
            string("existing_user_redirected_to_x_lite"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "putBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
            )
        ),
)

private fun getXLiteSettingItemsAdderFingerprint(migratedToXLite: Boolean) = object : Fingerprint(
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                returnType = "Z",
            ),
            opcode(
                opcode = Opcode.MOVE_RESULT,
                location = MatchAfterImmediately()
            ),
            resourceLiteral(
                type = ResourceType.STRING,
                name = if (migratedToXLite)
                    "settings_back_to_x_item_title"
                else
                    "x_lite_settings_back_to_x_item_title"
            )
        ),
) {}

@Suppress("unused")
val blockRedirectingToXLitePatch =
    bytecodePatch(
        name = "Block redirecting to X Lite",
        description = "Blocks redirecting to the new X Android UI on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, versionCheckPatch, resourceMappingPatch)

        execute {

            if (is_11_98_or_greater) {
                RedirectingToXLiteFlagCheckFingerprint.let {
                    it.method.apply {
                        val match = it.instructionMatches.last()
                        val index = match.index
                        val register = match.instruction.registersUsed[2]
                        addInstruction(index, "const v$register, 0x0")
                    }
                }

                getXLiteSettingItemsAdderFingerprint(is_12_07_or_greater).let {
                    it.method.apply {
                        val match = it.instructionMatches[1]
                        val index = match.index
                        val register = match.instruction.registersUsed[0]
                        addInstruction(index + 1, "const v$register, 0x1")
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
