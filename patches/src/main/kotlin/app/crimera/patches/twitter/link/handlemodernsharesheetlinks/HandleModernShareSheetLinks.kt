/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.handlemodernsharesheetlinks

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.is_11_40_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import java.util.logging.Logger

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
        dependsOn(versionCheckPatch)

        execute {

            // Checks in case someone uses this patch pre-11.4x.xx
            if (is_11_40_or_greater) {
                val contextualPostClass = "Lcom/x/models/ContextualPost;"
                val dummy = "#dummyReg"
                val dummy2 = "#linkReg"
                var callStatement =
                    """
                    invoke-static {v$dummy,v$dummy2}, $PATCHES_DESCRIPTOR/links/Urls;->hookShareSheetLink(Lcom/x/models/ContextualPost;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$dummy2
                    """.trimIndent()

                var shareSheetField: MutableField
                var contextualPostField: MutableField

                NewShareSheetLinkFingerprint.apply {
                    val className = classDef
                    shareSheetField = classDef.fields.first { it.type.contains("components/sharesheet") }
                    contextualPostField =
                        mutableClassDefBy(shareSheetField.type).fields.first { it.type == contextualPostClass }

                    val strIndex = stringMatches[1].index

                    method.apply {
                        val dummyRegister =
                            getInstruction(indexOfFirstInstruction(strIndex, Opcode.CONST_4)).registersUsed[0]
                        val linkRegister = getInstruction(strIndex).registersUsed[0]

                        val freeRegister = findFreeRegister(strIndex + 1, listOf(linkRegister, dummyRegister))

                        val injectCode =
                            callStatement
                                .replace(dummy, dummyRegister.toString())
                                .replace(dummy2, linkRegister.toString())

                        addInstructions(
                            strIndex + 1,
                            """
                            move-object/from16 v$freeRegister, p0
                            iget-object v$dummyRegister, v$freeRegister, $shareSheetField
                            iget-object v$dummyRegister, v$dummyRegister, $contextualPostField
                            $injectCode
                            """.trimIndent(),
                        )
                    }
                }

                NewShareSheetLinkFingerprint2.method.apply {
                    val firstGoto = indexOfFirstInstruction(Opcode.GOTO)

                    val shareSheetWInstruction = instructions[indexOfFirstInstruction(firstGoto, Opcode.IGET_OBJECT)]
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
                        iget-object v$dummyRegister, v$shareSheetWRegister, $contextualPostField
                        $injectCode
                        """.trimIndent(),
                    )
                }
            } else {
                return@execute Logger.getLogger(this::class.java.name).warning(
                    "The patch \"Hooks links on modern share sheet\" is force succeeded and does not work on any version below 11.40.\n",
                )
            }
        }
    }
