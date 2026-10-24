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
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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

            val messageCursorField = DmInfoJsonParserFingerprint.matchSingle().instructionMatches.last().fieldReference
            GetMessageCursorFieldNameExtensionFingerprint.changeFirstString(messageCursorField.name)

            // -------------------------

            val buttonEnumClassName = ThreadLongPressButtonsEnumInitFingerprint.instructionMatches.last().fieldReference.definingClass
            GetButtonEnumClassNameExtensionFingerprint.changeFirstString(classNameToExtension(buttonEnumClassName))

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
                val context = "Landroid/content/Context;"

                val userSessionFieldRef = classDef.fields.first { it.type == USER_SESSION_CLASS }
                val contextFieldRef = classDef.fields.first { it.type == context }

                method.apply {
                    val buttonParameterIndex = parameters.indexOfFirst { it.type == buttonEnumClassName } + 1
                    val directThreadKeyParameterIndex = parameters.indexOfFirst { it.type == directThreadKeyClassName } + 1
                    val threadInfoField = instructions
                        .filter { it.opcode == Opcode.IGET_OBJECT }
                        .mapNotNull { it.getReference<FieldReference>() }
                        .filter { it.type == messageCursorField.definingClass }
                        .distinct()
                        .singleOrNull() ?: throw PatchException("Could not resolve the chat menu thread info field")
                    val threadInfoClass = classDefBy(threadInfoField.definingClass)
                    val threadInfoParameters = parameters.indices.filter {
                        parameters[it].type == threadInfoClass.type || parameters[it].type in threadInfoClass.interfaces
                    }
                    val threadInfoParameterIndex = (threadInfoParameters.singleOrNull()
                        ?: throw PatchException("Could not resolve the chat menu thread info parameter")) + 1
                    GetThreadInfoFieldNameExtensionFingerprint.changeFirstString(threadInfoField.name)

                    // Hard coding register names as these instructions
                    // will be added on the first line.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v1, v0, $contextFieldRef
                        iget-object v2, v0, $userSessionFieldRef
                        
                        move-object/from16 v3, p$buttonParameterIndex
                        
                        move-object/from16 v4, p$threadInfoParameterIndex
                        move-object/from16 v5, p$directThreadKeyParameterIndex
                        
                        invoke-static {v1,v2,v3,v4,v5}, $EXTENSION_CLASS_NAME->buttonAction($context $USER_SESSION_CLASS Ljava/lang/Object;Ljava/lang/Object;$directThreadKeyClassName)Z
                        move-result v0
                        if-eqz v0, : piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }

            enableSettings("markChatAsRead")
        }
    }
