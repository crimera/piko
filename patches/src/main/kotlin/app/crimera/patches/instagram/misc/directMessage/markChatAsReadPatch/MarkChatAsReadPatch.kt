/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.ghostMode.DMSeenFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val markChatAsReadPatch =
    bytecodePatch(
        name = "Mark chat as read manually",
        description = "Adds option to mark a thread aka message as read manually",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(messageInfoEntity)
        execute {

            DMSeenFingerprint.apply {
                GetThreadSeenFunctionClassNameExtensionFingerprint.changeFirstString(classNameToExtension(classDef.type))

                method.apply {
                    GetThreadSeenFunctionMethodNameExtensionFingerprint.changeFirstString(name)

                    val dummyParameterClassName = parameters[1].toString()
                    GetThreadSeenDummyParameterClassNameExtensionFingerprint.changeFirstString(
                        classNameToExtension(dummyParameterClassName),
                    )
                }
            }

            DmInfoJsonParserFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val messageCursorIPutObjectIndex = indexOfFirstInstruction(strIndex, Opcode.IPUT_OBJECT)

                    val messageCursorFieldName = getInstruction(messageCursorIPutObjectIndex).fieldExtractor().name
                    GetMessageCursorFieldNameExtensionFingerprint.changeFirstString(messageCursorFieldName)
                }
            }

            // -------------------------

            val markAsReadButtonFieldRef: FieldReference
            val buttonEnumClassName: String
            ThreadLongPressButtonsEnumInitFingerprint.apply {
                val strIndex = stringMatches.first().index
                buttonEnumClassName = classDef.type
                GetButtonEnumClassNameExtensionFingerprint.changeFirstString(classNameToExtension(buttonEnumClassName))

                method.apply {
                    val markAsReadButtonSPutObjectIndex = indexOfFirstInstruction(strIndex, Opcode.SPUT_OBJECT)

                    markAsReadButtonFieldRef = getInstruction(markAsReadButtonSPutObjectIndex).getReference<FieldReference>()!!
                }
            }

            ThreadLongPressMuteButtonBuilderFingerprint.method.apply {
                val listParameterIndex = parameterTypes.indexOf("Ljava/util/List;")
                addInstructions(
                    0,
                    """
                    invoke-static {p$listParameterIndex}, $EXTENSION_CLASS_NAME->addButton(Ljava/util/List;)Ljava/util/List;
                    move-result-object p$listParameterIndex
                    
                    """.trimIndent(),
                )
            }

            ThreadLongPressButtonActionFingerprint.apply {
                val directThreadKeyClassName = "Lcom/instagram/model/direct/DirectThreadKey;"

                val userSessionFieldRef = classDef.fields.first { it.type == USER_SESSION_CLASS }
                method.apply {
                    val buttonParameterIndex = parameters.indexOfFirst { it.type == buttonEnumClassName } + 1
                    val directThreadKeyParameterIndex = parameters.indexOfFirst { it.type == directThreadKeyClassName } + 1
                    val threadInfoParameterIndex = directThreadKeyParameterIndex - 2

                    // Hard coding register names as these instructions
                    // will be added on the first line.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v0, v0, $userSessionFieldRef
                        
                        move-object/from16 v1, p$buttonParameterIndex
                        sget-object v2, $markAsReadButtonFieldRef
                        if-ne v1, v2, :piko
                        
                        move-object/from16 v1, p$threadInfoParameterIndex
                        move-object/from16 v2, p$directThreadKeyParameterIndex
                        
                        invoke-static {v0,v1,v2}, $EXTENSION_CLASS_NAME->markAsRead($USER_SESSION_CLASS Ljava/lang/Object;$directThreadKeyClassName)V
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }

            enableSettings("markChatAsRead")
        }
    }
