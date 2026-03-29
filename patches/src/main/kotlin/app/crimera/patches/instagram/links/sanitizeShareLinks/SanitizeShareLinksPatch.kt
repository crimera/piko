/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

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

            val responseImplFingerprint =
                listOf(
                    StoryUrlResponseImplFingerprint,
                    LiveUrlResponseImplFingerprint,
                )

            responseImplFingerprint.forEach { fingerprint ->
                fingerprint.method.apply {
                    val returnObjectInst = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                    val index = returnObjectInst.location.index
                    val urlRegister = returnObjectInst.registersUsed[0]

                    addInstructions(index, EXTENSION_METHOD.format(urlRegister, urlRegister, urlRegister))
                }
            }

            enableSettings("sanitizeShareLinks")
        }
    }
