/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.ChatActionBarBuilderFingerprint
import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"
private const val DIRECT_THREAD_KEY = "Lcom/instagram/model/direct/DirectThreadKey;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
        default = true,
    ) {
        // userDataEntity is deliberately not a dependency: it only backs Hook 6's username
        // enrichment, so a break in its resolver must not abort capture.
        dependsOn(settingsPatch, chatActionBarButtonPatch, directItemEntity, deletedMessagesResourcePatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Each hook is independently runCatching-wrapped: the hooks are separate capture points,
            // so a drifted anchor should skip only that hook, never abort the patch.

            // Hook 1: REST/JSON path — inject at return of parseFromJson (v426) or unsafeParseFromJson (v430+).
            runCatching {
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
            }

            // Hook 2: MQTT/MSys real-time path, which REST never touches. The delta class comes from
            // the method's own second parameter, then its converter is found by signature.
            runCatching {
                val deltaClass = DirectItemPostprocessFingerprint.method.parameterTypes[1].toString()
                fun isConverter(m: com.android.tools.smali.dexlib2.iface.Method) =
                    AccessFlags.STATIC.isSet(m.accessFlags) &&
                        m.returnType == DIRECT_THREAD_KEY &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0].toString() == deltaClass
                val deltaThreadIdField =
                    mutableClassDefBy { cd -> cd.methods.any { isConverter(it) } }
                    .methods.first { isConverter(it) }
                    .instructions
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
            }

            // Hook 4: SQLite DAO delete — inject at entry so our DB record is still present when the hook fires.
            runCatching {
                DirectItemDbHideFingerprint.method.apply {
                    val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 2)
                    val r0 = regs.getFreeRegister()
                    val r1 = regs.getFreeRegister()

                    addInstructions(
                        0,
                        """
                        move-object/from16 v$r0, p2
                        move-object/from16 v$r1, p3
                        invoke-static {v$r0, v$r1}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/String;Ljava/lang/String;)V
                        """.trimIndent(),
                    )
                }
            }

            runCatching {
                val threadIdField =
                    mutableClassDefBy { it.type == DIRECT_THREAD_KEY }
                        .methods.first { it.name == "toString" }
                        .instructions.toList()
                        .let { insns ->
                            val literalIndex =
                                insns.indexOfFirst {
                                    (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                        (it as ReferenceInstruction).reference.toString().contains("mThreadId")
                                }
                            insns.drop(literalIndex + 1).first {
                                it.opcode == Opcode.IGET_OBJECT &&
                                    (it as ReferenceInstruction).reference
                                        .let { r -> r is FieldReference && r.type == "Ljava/lang/String;" }
                            }
                        }.let { (it as ReferenceInstruction).reference as FieldReference }

                ChatActionBarBuilderFingerprint.method.apply {
                    val insns = instructions.toList()
                    val keyConverter =
                        insns.first {
                            it.opcode == Opcode.INVOKE_STATIC &&
                                (it as ReferenceInstruction).reference
                                    .let { r -> r is MethodReference && r.returnType == DIRECT_THREAD_KEY }
                        }.let { (it as ReferenceInstruction).reference as MethodReference }
                    val descriptorType = keyConverter.parameterTypes[0].toString()
                    val descriptorField =
                        insns.first {
                            it.opcode == Opcode.IGET_OBJECT &&
                                (it as ReferenceInstruction).reference
                                    .let { r -> r is FieldReference && r.type == descriptorType }
                        }.let { (it as ReferenceInstruction).reference as FieldReference }
                    val viewModelType = descriptorField.definingClass

                    // Inject after the view-model's first consumer, where its two producers rejoin. A
                    // branch label sits on that call, so anything inserted before it is jumped over.
                    val joinInstruction =
                        insns.first {
                            it.opcode == Opcode.INVOKE_STATIC &&
                                (it as ReferenceInstruction).reference
                                    .let { r -> r is MethodReference && r.parameterTypes.firstOrNull()?.toString() == viewModelType }
                        }
                    val viewModelRegister = joinInstruction.registersUsed[0]
                    // A move-result must stay welded to its call, so step past it when present.
                    val afterJoinIndex =
                        joinInstruction.location.index + 1
                    val injectIndex =
                        if (insns[afterJoinIndex].opcode == Opcode.MOVE_RESULT ||
                            insns[afterJoinIndex].opcode == Opcode.MOVE_RESULT_OBJECT ||
                            insns[afterJoinIndex].opcode == Opcode.MOVE_RESULT_WIDE
                        ) {
                            afterJoinIndex + 1
                        } else {
                            afterJoinIndex
                        }
                    val reg = getFreeRegisterProvider(injectIndex, 1).getFreeRegister()

                    // The skip target must be an ExternalLabel; an in-snippet label is not anchored
                    // where it is written when inserting mid-method.
                    addInstructionsWithLabels(
                        injectIndex,
                        """
                        if-eqz v$viewModelRegister, :piko_no_thread
                        iget-object v$reg, v$viewModelRegister, $viewModelType->${descriptorField.name}:$descriptorType
                        if-eqz v$reg, :piko_no_thread
                        invoke-static {v$reg}, ${keyConverter.definingClass}->${keyConverter.name}($descriptorType)$DIRECT_THREAD_KEY
                        move-result-object v$reg
                        if-eqz v$reg, :piko_no_thread
                        iget-object v$reg, v$reg, $DIRECT_THREAD_KEY->${threadIdField.name}:Ljava/lang/String;
                        invoke-static {v$reg}, $HOOK_CLASS->noteOpenThreadId(Ljava/lang/String;)V
                        """.trimIndent(),
                        ExternalLabel("piko_no_thread", insns[injectIndex]),
                    )
                }
            }

            // Hook 6: harvest participant usernames from the thread deserializer's "users" iput-object.
            runCatching {
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
            }

            enableSettings("saveDeletedMessages")
        }
    }
