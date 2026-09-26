/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
        default = true,
    ) {
        // userDataEntity is deliberately not a dependency: it only backs Hook 6's username
        // enrichment, so a break in its resolver must not abort capture.
        dependsOn(settingsPatch, directItemEntity, deletedMessagesResourcePatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Hook 1: REST/JSON path — inject at return of parseFromJson (v426) or unsafeParseFromJson (v430+).
            val parser =
                DirectItemFieldParserFingerprint.classDef.methods.singleOrNull {
                    it.name == "parseFromJson" || it.name == "unsafeParseFromJson"
                } ?: throw PatchException("Expected one DirectItem JSON parser")
            parser.apply {
                val returnObjInstruction = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                    ?: throw PatchException("DirectItem parser has no object return")
                val returnRegisters = returnObjInstruction.registersUsed
                if (returnRegisters.size != 1) {
                    throw PatchException("DirectItem parser return has an unexpected register count")
                }
                val itemRegister = returnRegisters[0]

                addInstructions(
                    returnObjInstruction.location.index,
                    """
                    invoke-static {v$itemRegister}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 2: MQTT/MSys real-time path, which REST never touches. Hook at entry because
            // normal branches jump directly to return instructions and skip code inserted before them.
            val postprocess = DirectItemPostprocessFingerprint.method
            if (postprocess.parameterTypes.size != 2 ||
                postprocess.parameterTypes[0].toString() != USER_SESSION_CLASS
            ) {
                throw PatchException("DirectItem postprocess has unexpected parameters")
            }
            val deltaType = postprocess.parameterTypes[1].toString()
            val deltaStringFields =
                mutableClassDefBy { definition -> definition.type == deltaType }
                    .fields.filter { field ->
                        field.type == "Ljava/lang/String;" &&
                            !AccessFlags.STATIC.isSet(field.accessFlags)
                    }
            if (deltaStringFields.size != 1) {
                throw PatchException(
                    "Expected one delta thread-id field in $deltaType, " +
                        "found ${deltaStringFields.size}",
                )
            }
            DeltaThreadIdFieldExtensionFingerprint.changeString(
                "deltaThreadIdField",
                deltaStringFields.single().name,
            )
            postprocess.apply {
                addInstructions(
                    0,
                    """
                    invoke-static/range {p0 .. p2}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 4: SQLite DAO delete — inject at entry so our DB record is still present when the hook fires.
            DirectItemDbHideFingerprint.method.apply {
                if (parameterTypes.size < 3 ||
                    parameterTypes[1].toString() != "Ljava/lang/String;" ||
                    parameterTypes[2].toString() != "Ljava/lang/String;"
                ) {
                    throw PatchException("DirectItem DB hide identifiers have an unexpected signature")
                }
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 2)
                val serverIdRegister = regs.getFreeRegister()
                val clientContextRegister = regs.getFreeRegister()

                addInstructions(
                    0,
                    """
                    move-object/from16 v$serverIdRegister, p2
                    move-object/from16 v$clientContextRegister, p3
                    invoke-static {v$serverIdRegister, v$clientContextRegister}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/String;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // Username enrichment is optional because numeric sender IDs remain usable. If the
            // fingerprint matches, its structure must still be exact so a wrong hook never ships.
            ThreadUsersDispatchFingerprint.methodOrNull?.apply {
                val insns = instructions.toList()
                val usersKeyIndex =
                    insns.indexOfFirst {
                        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                            (it as ReferenceInstruction).reference.toString() == "users"
                    }
                if (usersKeyIndex < 0) {
                    throw PatchException("Thread users dispatch has no users key")
                }
                val listPutInstruction =
                    insns.drop(usersKeyIndex + 1).firstOrNull {
                        it.opcode == Opcode.IPUT_OBJECT &&
                            (it as ReferenceInstruction).reference.toString().endsWith(":Ljava/util/List;")
                    } ?: throw PatchException("Thread users dispatch has no users list field")
                val listRegisters = listPutInstruction.registersUsed
                if (listRegisters.size != 2) {
                    throw PatchException("Thread users list write has an unexpected register count")
                }
                val parsedUsersRegister = listRegisters[0]
                val putIndex = listPutInstruction.location.index
                val usersRegister = getFreeRegisterProvider(putIndex + 1, 1).getFreeRegister()
                addInstructions(
                    putIndex + 1,
                    """
                    move-object/from16 v$usersRegister, v$parsedUsersRegister
                    invoke-static {v$usersRegister}, $HOOK_CLASS->noteThreadUsers(Ljava/util/List;)V
                    """.trimIndent(),
                )
            }

            enableSettings("saveDeletedMessages")
        }
    }
