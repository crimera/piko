/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val sanitizeShareLinksPatch =
    bytecodePatch(
        name = "Sanitize share links",
    ) {

        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val EXTENSION_METHOD =
                """
                invoke-static/range { v%s .. v%s }, ${LINKS_DESCRIPTOR}->sanitizeUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v%s
                """.trimIndent()

            val jsonParserFingerprints =
                listOf(
                    PermalinkResponseJsonParserFingerprint,
                    ProfileUrlResponseJsonParserFingerprint,
                )

            jsonParserFingerprints.forEach { fingerprint ->
                val strIndex = fingerprint.stringMatches[0].index
                fingerprint.method.apply {
                    val strIPutObjectIndex = indexOfFirstInstruction(strIndex, Opcode.IPUT_OBJECT)
                    val urlRegister = instructions[strIPutObjectIndex].registersUsed[0]

                    addInstructions(strIPutObjectIndex, EXTENSION_METHOD.format(urlRegister, urlRegister, urlRegister))
                }
            }

            val audioUrlParserMatch = AudioUrlResponseJsonParserFingerprint.matchAll(1..1).single()
            val audioUrlStringIndex = audioUrlParserMatch.stringMatches.single().index
            audioUrlParserMatch.method.apply {
                val audioUrlAssignmentIndex =
                    instructions
                        .withIndex()
                        .filter { (index, instruction) ->
                            index > audioUrlStringIndex &&
                                instruction.opcode == Opcode.IPUT_OBJECT &&
                                ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type ==
                                "Ljava/lang/String;"
                        }.map { it.index }
                        .singleOrNull()
                        ?: throw PatchException("Expected one audio share URL assignment")
                val urlRegister = instructions[audioUrlAssignmentIndex].registersUsed[0]

                addInstructions(
                    audioUrlAssignmentIndex,
                    EXTENSION_METHOD.format(urlRegister, urlRegister, urlRegister),
                )
            }

            val responseImplFingerprint =
                listOf(
                    StoryItemThirdPartySharingUrlResponseImplFingerprint,
                    LiveThirdPartySharingUrlResponseImplFingerprint,
                )

            responseImplFingerprint.forEach { fingerprint ->
                fingerprint.method.apply {
                    val returnObjectInst = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                    val index = returnObjectInst.location.index
                    val urlRegister = returnObjectInst.registersUsed[0]

                    addInstructions(index, EXTENSION_METHOD.format(urlRegister, urlRegister, urlRegister))
                }
            }

            val highlightShareUrlRequestMatch =
                HighlightShareUrlRequestFingerprint.matchAll(1..1).single()
            val highlightCallbackType =
                highlightShareUrlRequestMatch.method.instructions
                    .mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.NEW_INSTANCE) {
                            return@mapNotNull null
                        }
                        ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type
                    }.singleOrNull()
                    ?: throw PatchException("Expected one highlight share URL callback class")

            val highlightCallbackMethod =
                mutableClassDefBy(highlightCallbackType)
                    .methods
                    .singleOrNull { method ->
                        method.returnType == "V" &&
                            method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;")
                    } ?: throw PatchException("Expected one highlight share URL callback method")

            val highlightUrlResultIndex =
                highlightCallbackMethod.instructions
                    .withIndex()
                    .filter { (index, instruction) ->
                        if (instruction.opcode != Opcode.INVOKE_INTERFACE) {
                            return@filter false
                        }
                        val methodReference =
                            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        methodReference?.returnType == "Ljava/lang/String;" &&
                            highlightCallbackMethod.instructions.getOrNull(index + 1)?.opcode ==
                                Opcode.MOVE_RESULT_OBJECT
                    }.map { it.index + 1 }
                    .singleOrNull()
                    ?: throw PatchException("Expected one highlight share URL result")

            val highlightUrlRegister =
                highlightCallbackMethod.instructions[highlightUrlResultIndex]
                    .registersUsed
                    .singleOrNull()
                    ?: throw PatchException("Expected one highlight share URL result register")
            highlightCallbackMethod.addInstructions(
                highlightUrlResultIndex + 1,
                EXTENSION_METHOD.format(
                    highlightUrlRegister,
                    highlightUrlRegister,
                    highlightUrlRegister,
                ),
            )

            enableSettings("sanitizeShareLinks")
        }
    }
