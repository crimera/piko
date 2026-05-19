/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.moreOptionsOnPost

import app.crimera.patches.instagram.misc.distractionFree.doubleTap.PostOnSingleTapConfirmedFingerprint
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

@Suppress("unused")
val moreOptionsOnPostPatch =
    bytecodePatch(
        name = "More options on post",
        description = "Adds more options on post, like copy description by long pressing on post",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            PostOnSingleTapConfirmedFingerprint.apply {
                // And instruction to get post data class.
                var firstIGetObjFieldInstruction: FieldReference

                val postObjectDefiningClass: String

                classDef.methods.first { it.name == "onDoubleTap" }.apply {
                    val firstIGetInstructionIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                    firstIGetObjFieldInstruction = getInstruction(firstIGetInstructionIndex).getReference<FieldReference>()!!

                    val mediaObjectFieldExtraction = getInstruction(firstIGetInstructionIndex + 2).fieldExtractor()
                    MediaFieldNameExtensionFingerprint.changeFirstString(mediaObjectFieldExtraction.name)
                    postObjectDefiningClass = mediaObjectFieldExtraction.definingClass
                }

                // Add custom onLongPress method.
                classDef.methods.add(
                    ImmutableMethod(
                        originalClassDef.type,
                        "onLongPress",
                        listOf(ImmutableMethodParameter("Landroid/view/MotionEvent;", null, null)),
                        "V",
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(2),
                    ).toMutable().apply {
                        addInstructions(
                            0,
                            """
                            iget-object v0, p0, $firstIGetObjFieldInstruction
                            invoke-static {v0}, $EXTENSION_CLASS_DESCRIPTOR->postOnLongPress(Ljava/lang/Object;)V
                            return-void
                            """.trimIndent(),
                        )
                    },
                )

                val postObjectClass = mutableClassDefBy { it.type == extensionToClassName(postObjectDefiningClass) }
                val postObjectClassFields = postObjectClass.fields

                val contextFieldName = postObjectClassFields.first { it.type == "Landroid/content/Context;" }.name
                ContextFieldNameExtensionFingerprint.changeFirstString(contextFieldName)

                val currentMediaIndexFieldName = postObjectClassFields.first { it.type == "I" }.name
                CurrentMediaIndexFieldNameExtensionFingerprint.changeFirstString(currentMediaIndexFieldName)

                // Enable long press on all posts.
                postObjectClass.methods.first().apply {
                    val firstInvokeVirtualIndex = indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL)
                    val firstInvokeVirtualInstruction = getInstruction(firstInvokeVirtualIndex)

                    val onLongPressEnableBoolRegister = firstInvokeVirtualInstruction.registersUsed[1]

                    addInstruction(
                        firstInvokeVirtualIndex,
                        """
                        const v$onLongPressEnableBoolRegister, 0x1    
                        """.trimIndent(),
                    )
                }
            }
            enableSettings("moreOptionsOnPost")
        }
    }
