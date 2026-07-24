/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.inlinebar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private object CustomiseInlineBarFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters =
        listOf(
            string("bookmarks_in_timelines_enabled"),
        ),
)

private object XLiteInlineActionBarClassFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lcom/x/models/ContextualPost;",
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lcom/x/subscriptions/SubscriptionsFeatures;",
            ),
        ),
)

private object CustomiseXLiteInlineBarFingerprint : Fingerprint(
    classFingerprint = XLiteInlineActionBarClassFingerprint,
    parameters = listOf("Landroidx/compose/runtime/Composer;"),
    filters =
        listOf(
            methodCall(
                smali =
                    "Lcom/x/models/ContextualPost;->getCanonicalPost()Lcom/x/models/CanonicalPost;",
            ),
            methodCall(
                smali =
                    "Lcom/x/models/ContextualPost;->getInlineActionEntry()Lkotlinx/collections/immutable/c;",
            ),
            methodCall(
                smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z",
            ),
        ),
)

@Suppress("unused")
val customiseInlineBarPatch =
    bytecodePatch(
        name = "Customize Inline action Bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseInlineBarFingerprint.method
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->inlineBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructions(returnObj_loc, METHOD)

            val xLiteMatches = CustomiseXLiteInlineBarFingerprint.matchAll()
            if (xLiteMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite inline action state builder, found ${xLiteMatches.size}",
                )
            }

            xLiteMatches.single().let { match ->
                val method = match.method
                val addIndex = match.instructionMatches.last().index
                val listRegister = method.getInstruction(addIndex).registersUsed.first()
                val conversionInstruction =
                    method.instructions
                        .drop(addIndex + 1)
                        .firstOrNull { instruction ->
                            instruction.opcode == Opcode.INVOKE_STATIC &&
                                instruction.registersUsed.firstOrNull() == listRegister
                        } ?: throw PatchException("X-Lite inline action list conversion not found")
                val conversionIndex = conversionInstruction.location.index
                val conversionReference =
                    conversionInstruction.getReference<MethodReference>()
                        ?: throw PatchException("X-Lite inline action list conversion has no method reference")
                val resultIndex = conversionIndex + 1
                val resultInstruction = method.getInstruction<OneRegisterInstruction>(resultIndex)
                if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("X-Lite inline action list conversion result not found")
                }
                val resultRegister = resultInstruction.registerA

                // Branches exiting the mapping loop target the immutable-list conversion.
                // Inject after its move-result so those branches cannot skip the hook.
                method.addInstructions(
                    resultIndex + 1,
                    """
                        invoke-static/range {v$resultRegister .. v$resultRegister}, $CUSTOMISE_DESCRIPTOR;->inlineBar(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$resultRegister
                        invoke-static/range {v$resultRegister .. v$resultRegister}, $conversionReference
                        move-result-object v$resultRegister
                    """.trimIndent(),
                )
            }

            enableSettings("inlineBarCustomisation")
        }
    }
