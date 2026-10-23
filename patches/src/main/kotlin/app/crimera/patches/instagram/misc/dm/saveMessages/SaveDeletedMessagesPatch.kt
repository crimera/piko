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
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
        default = true,
    ) {
        dependsOn(settingsPatch, directItemEntity, deletedMessagesResourcePatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Hook 1: REST/JSON path, inject at the return of the parser.
            DirectItemJsonParserFingerprint.matchSingle().let {
                val returnObject = it.instructionMatches.last()
                val itemRegister = returnObject.getInstruction<OneRegisterInstruction>().registerA

                it.method.addInstructionsAtControlFlowLabel(
                    returnObject.index,
                    """
                    invoke-static {v$itemRegister}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 2: MQTT/MSys real-time path, which REST never touches. Hook at entry because
            // normal branches jump directly to return instructions and skip code inserted before them.
            val postprocess = DirectItemPostprocessFingerprint.method
            val deltaType = postprocess.parameterTypes[1].toString()
            val deltaStringFields =
                classDefBy(deltaType)
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
            postprocess.addInstructions(
                0,
                """
                invoke-static/range {p0 .. p2}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                """.trimIndent(),
            )

            // Hook 4: SQLite DAO delete, inject at entry so our DB record is still present when the hook fires.
            DirectItemDbHideFingerprint.method.apply {
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

            // Username enrichment is optional because numeric sender IDs remain usable.
            ThreadUsersDispatchFingerprint.matchOrNull()?.let { match ->
                match.method.apply {
                    // The list write is out of line, after other JSON fields. Follow the
                    // array guard to its completion block instead of selecting the next list write.
                    val arrayGuard = getInstruction<BuilderOffsetInstruction>(match.instructionMatches[1].index)
                    val putIndex = indexOfFirstInstructionOrThrow(
                        arrayGuard.target.location.index,
                        fieldAccess(
                            definingClass = parameterTypes.first().toString(),
                            type = "Ljava/util/List;",
                            opcode = Opcode.IPUT_OBJECT,
                        ),
                    )
                    val parsedUsersRegister = getInstruction<TwoRegisterInstruction>(putIndex).registerA
                    val usersRegister = getFreeRegisterProvider(putIndex + 1, 1).getFreeRegister()
                    addInstructions(
                        putIndex + 1,
                        """
                        move-object/from16 v$usersRegister, v$parsedUsersRegister
                        invoke-static {v$usersRegister}, $HOOK_CLASS->noteThreadUsers(Ljava/util/List;)V
                        """.trimIndent(),
                    )
                }
            }

            enableSettings("saveDeletedMessages")
        }
    }
