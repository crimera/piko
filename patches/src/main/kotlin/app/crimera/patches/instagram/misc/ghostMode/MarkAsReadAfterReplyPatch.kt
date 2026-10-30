/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.entity.directItem.DirectThreadKeyToStringFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val markAsReadAfterReplyPatch = bytecodePatch(
    name = "Mark as read after reply",
    description = "Adds an option to mark chats as read after replying while viewing DMs anonymously.",
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(viewDmAnonymouslyPatch)

    execute {
        val seen = DMSeenFingerprint.method
        if (seen.parameterTypes.size != 5 || seen.parameterTypes[0] != USER_SESSION_CLASS ||
            !seen.parameterTypes[1].startsWith("L") ||
            seen.parameterTypes.drop(2).any { it != "Ljava/lang/String;" }
        ) {
            throw PatchException("Unexpected DM seen request parameters")
        }
        seen.addInstructions(
            0,
            "invoke-static/range {p0 .. p4}, $REPLY_READ_EXTENSION->onSeenRequest(${USER_SESSION_CLASS}Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        )
        ReplyReadSendReceiptFingerprint.method.addInstructions(
            0,
            """
            check-cast p1, ${seen.parameterTypes[1]}
            invoke-static/range {p0 .. p4}, $seen
            """.trimIndent(),
        )

        val threadId = DirectThreadKeyToStringFingerprint.instructionMatches.first().fieldReference
        ReplyReadThreadIdFingerprint.method.addInstructions(
            0,
            """
            iget-object p0, p0, $threadId
            return-object p0
            """.trimIndent(),
        )

        val textSendClass = DmTextSendNameFingerprint.originalClassDef
        val sendClass = classDefBy(textSendClass.superclass
            ?: throw PatchException("Could not resolve the DM send mutation class"))
        val threadsGetter = sendClass.methods.singleOrNull { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.isEmpty() && method.returnType == "Ljava/util/List;" &&
                method.implementation?.instructions?.toList()?.let { instructions ->
                    instructions.size == 2 && instructions[0].opcode == Opcode.IGET_OBJECT &&
                        instructions[0].getReference<FieldReference>()?.type == "Ljava/util/List;" &&
                        instructions[1].opcode == Opcode.RETURN_OBJECT
                } == true
        } ?: throw PatchException("Could not uniquely resolve the DM send thread list getter")

        DmMutationSuccessFingerprint.let { success ->
            val sessionField = success.originalClassDef.fields.singleOrNull {
                it.type == USER_SESSION_CLASS && !AccessFlags.STATIC.isSet(it.accessFlags)
            } ?: throw PatchException("Could not uniquely resolve the DM success session field")
            success.method.apply {
                val registers = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 2)
                val sessionRegister = registers.getFreeRegister()
                val threadsRegister = registers.getFreeRegister()
                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v$threadsRegister, p2
                    instance-of v$sessionRegister, v$threadsRegister, ${sendClass.type}
                    if-eqz v$sessionRegister, :piko_continue
                    check-cast v$threadsRegister, ${sendClass.type}
                    invoke-virtual {v$threadsRegister}, $threadsGetter
                    move-result-object v$threadsRegister
                    move-object/from16 v$sessionRegister, p0
                    iget-object v$sessionRegister, v$sessionRegister, $sessionField
                    invoke-static {v$sessionRegister, v$threadsRegister}, $REPLY_READ_EXTENSION->onReplySent(${USER_SESSION_CLASS}Ljava/util/List;)V
                    """.trimIndent(),
                    ExternalLabel("piko_continue", getInstruction(0)),
                )
            }
        }
        enableSettings("markAsReadAfterReply")
    }
}
