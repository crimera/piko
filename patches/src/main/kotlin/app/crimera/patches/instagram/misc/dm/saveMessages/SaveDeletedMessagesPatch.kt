/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.DMActionBarBuilderFingerprint
import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/dm/SavedMessagesHook;"
private const val DIRECT_THREAD_KEY = "Lcom/instagram/model/direct/DirectThreadKey;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
    ) {
        dependsOn(settingsPatch, dmActionBarButtonPatch, directItemEntity, userDataEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Hook 1: REST/JSON path — inject at return of parseFromJson (v426) or unsafeParseFromJson (v430+).
            DirectItemFieldParserFingerprint.classDef.methods
                .first { it.name == "parseFromJson" || it.name == "unsafeParseFromJson" }.apply {
                val returnObjInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                val returnObjIndex = returnObjInstruction.location.index
                val itemRegister = returnObjInstruction.registersUsed[0]

                addInstructions(
                    returnObjIndex,
                    """
                    invoke-static {v$itemRegister}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 2: MQTT/MSys real-time path — A0P post-processing step, never touched by REST.
            // Derive delta class from A0P's second parameter, then find the static converter
            // (delta → DirectThreadKey) by signature search — no dependency on DMActionBarBuilderFingerprint.
            val deltaClass = DirectItemPostprocessFingerprint.method.parameterTypes[1].toString()
            val deltaThreadIdField =
                mutableClassDefBy { cd ->
                    cd.methods.any { m ->
                        AccessFlags.STATIC.isSet(m.accessFlags) &&
                            m.returnType == DIRECT_THREAD_KEY &&
                            m.parameterTypes.size == 1 &&
                            m.parameterTypes[0].toString() == deltaClass
                    }
                }.methods.first { m ->
                    AccessFlags.STATIC.isSet(m.accessFlags) &&
                        m.returnType == DIRECT_THREAD_KEY &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0].toString() == deltaClass
                }.instructions
                    .first {
                        it.opcode == Opcode.IGET_OBJECT &&
                            (it as ReferenceInstruction).reference
                                .let { r -> r is FieldReference && r.type == "Ljava/lang/String;" }
                    }.let { (it as ReferenceInstruction).reference as FieldReference }

            DirectItemPostprocessFingerprint.method.apply {
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 3)
                val rItem = regs.getFreeRegister()
                val rDelta = regs.getFreeRegister()
                val rTid = regs.getFreeRegister()
                // p2 (MSys delta) is null on some A0P calls — pass null thread-id hint in that case.
                addInstructions(
                    0,
                    """
                    move-object/from16 v$rItem, p0
                    const/4 v$rTid, 0x0
                    move-object/from16 v$rDelta, p2
                    if-eqz v$rDelta, :piko_no_delta
                    iget-object v$rTid, v$rDelta, $deltaClass->${deltaThreadIdField.name}:Ljava/lang/String;
                    :piko_no_delta
                    invoke-static {v$rItem, v$rTid}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // Hook 4: SQLite DAO delete — inject at entry so our DB record is still present when the hook fires.
            DirectItemDbHideFingerprint.method.apply {
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 3)
                val r0 = regs.getFreeRegister()
                val r1 = regs.getFreeRegister()
                val r2 = regs.getFreeRegister()

                addInstructions(
                    0,
                    """
                    move-object/from16 v$r0, p0
                    move-object/from16 v$r1, p2
                    move-object/from16 v$r2, p3
                    invoke-static {v$r0, v$r1, v$r2}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // Hook 5: pass the action-bar controller (p0) to the extension — thread-id is resolved lazily from its object graph.
            DMActionBarBuilderFingerprint.method.apply {
                val reg = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
                addInstructions(
                    0,
                    """
                    move-object/from16 v$reg, p0
                    invoke-static {v$reg}, $HOOK_CLASS->noteOpenThreadController(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 6: harvest participant usernames from the thread deserializer's "users" iput-object.
            ThreadUsersDispatchFingerprint.method.apply {
                val insns = instructions.toList()
                val usersKeyIndex =
                    insns.indexOfFirst {
                        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                            (it as ReferenceInstruction).reference.toString() == "users"
                    }
                if (usersKeyIndex < 0) return@apply
                val listPutInstruction =
                    insns.drop(usersKeyIndex + 1).firstOrNull {
                        it.opcode == Opcode.IPUT_OBJECT &&
                            (it as ReferenceInstruction).reference.toString().endsWith(":Ljava/util/List;")
                    } ?: return@apply
                val listRegister = listPutInstruction.registersUsed[0]
                val putIndex = listPutInstruction.location.index
                val free = getFreeRegisterProvider(putIndex + 1, 1).getFreeRegister()
                addInstructions(
                    putIndex + 1,
                    """
                    move-object/from16 v$free, v$listRegister
                    invoke-static {v$free}, $HOOK_CLASS->noteThreadUsers(Ljava/util/List;)V
                    """.trimIndent(),
                )
            }

            enableSettings("saveDeletedMessages")
        }
    }
