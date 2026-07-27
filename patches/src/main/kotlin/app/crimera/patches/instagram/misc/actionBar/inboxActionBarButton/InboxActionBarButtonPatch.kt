/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.utils.getReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

object InboxActionBarBuilderFingerprint : Fingerprint(
    strings = listOf("PrebindActionBar"),
)

val inboxActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on Inbox action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(resourceMappingPatch)

        execute {

            InboxActionBarBuilderFingerprint.method.apply {
                instructions.filter { it.opcode == Opcode.CHECK_CAST }.firstOrNull {
                    val typeRef = it.getReference<TypeReference>()!!.type
                    if (typeRef == "Lcom/instagram/igds/components/actionbar/IgdsActionBar;") {
                        val viewGroupRegister = it.registersUsed[0]
                        addInstructions(
                            it.location.index + 1,
                            """
                            invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR->inboxActionBarButton(Landroid/view/ViewGroup;)V
                            """.trimIndent(),
                        )
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }
