package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val sanitizeShareLinksPatch = bytecodePatch(
    name = "Sanitize share links",
) {

    dependsOn(settingsPatch)

    compatibleWith("com.instagram.android")

    execute {

        val fingerprints = listOf(
            PermalinkResponseJsonParserFingerprint,
            StoryUrlResponseJsonParserFingerprint,
            ProfileUrlResponseJsonParserFingerprint,
            LiveUrlResponseJsonParserFingerprint
        )

        fingerprints.forEach { fingerprint->
            val strIndex = fingerprint.stringMatches[0].index
            fingerprint.method.apply {
                val strIPutObjectIndex = indexOfFirstInstruction(strIndex,Opcode.IPUT_OBJECT)
                val urlRegister = instructions[strIPutObjectIndex].registersUsed[0]

                addInstructions(strIPutObjectIndex,"""
                    invoke-static/range { v$urlRegister .. v$urlRegister }, ${Constants.LINKS_DESCRIPTOR}->sanitizeUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                """.trimIndent())
            }
        }

        enableSettings("sanitizeShareLinks")
    }
}