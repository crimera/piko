/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.link.handlemodernsharesheetlinks

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.extractDescriptors
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c

internal object NewShareSheetLinkFingerprint : Fingerprint(
    strings =
        listOf(
            "tweet-",
            "https://x.com/i/status/",
        ),
)

internal object NewShareSheetLinkFingerprint2 : Fingerprint(
    strings =
        listOf(
            "https://x.com/i/status/",
            "https://x.com/i/lists/",
            "https://x.com/i/trending/",
        ),
)

@Suppress("unused")
val handleModernShareSheetLinks =
    bytecodePatch(
        description = "Hooks links on modern share sheet ( after 11.4x.xx )",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {

            // Try catch is used in case someone uses this patch pre-11.4x.xx
            try {
                val dummy = "#dummyReg"
                val dummy2 = "#linkReg"
                var callStatement =
                    """
                    ->d:Lcom/x/models/ContextualPost;
                            invoke-static {v$dummy,v$dummy2}, $PATCHES_DESCRIPTOR/links/Urls;->hookShareSheetLink(Lcom/x/models/ContextualPost;Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v$dummy2
                    """.trimIndent()

                NewShareSheetLinkFingerprint.apply {
                    val className = classDef
                    val shareSheetField = classDef.fields.first { it.type.contains("components/sharesheet") }
                    val strIndex = stringMatches[1].index

                    method.apply {
                        val dummyRegister =
                            getInstruction(indexOfFirstInstruction(strIndex, Opcode.CONST_4)).registersUsed[0]
                        val linkRegister = getInstruction(strIndex).registersUsed[0]

                        val injectCode =
                            callStatement
                                .replace(dummy, dummyRegister.toString())
                                .replace(dummy2, linkRegister.toString())
                        addInstructions(
                            strIndex + 1,
                            """
                            iget-object v$dummyRegister, v0, $shareSheetField
                            iget-object v$dummyRegister, v$dummyRegister, ${shareSheetField.type}$injectCode
                            
                            """.trimIndent(),
                        )
                    }
                }

                NewShareSheetLinkFingerprint2.method.apply {
                    val firstGoto = indexOfFirstInstruction(Opcode.GOTO)

                    val shareSheetWInstruction = instructions[indexOfFirstInstruction(firstGoto, Opcode.IGET_OBJECT)]
                    val shareSheetWClassName = (shareSheetWInstruction as Instruction22c).reference.extractDescriptors()[0]
                    val shareSheetWRegister = shareSheetWInstruction.registersUsed[1]

                    val dummyRegister = getInstruction(firstGoto - 2).registersUsed[0]
                    val linkRegister = getInstruction(firstGoto - 1).registersUsed[0]

                    val injectCode =
                        callStatement
                            .replace(dummy, dummyRegister.toString())
                            .replace(dummy2, linkRegister.toString())

                    addInstructions(
                        firstGoto,
                        """
                        iget-object v$dummyRegister, v$shareSheetWRegister, $shareSheetWClassName$injectCode
                        """.trimIndent(),
                    )
                }
            } catch (_: Exception) {
            }
        }
    }
