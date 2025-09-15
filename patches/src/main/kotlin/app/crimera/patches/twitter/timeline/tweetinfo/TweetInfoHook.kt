package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal val tweetInfoHookFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        custom { it, _ ->
            it.definingClass == "Lcom/twitter/api/model/json/core/JsonApiTweet\$\$JsonObjectMapper;" && it.name == "parse"
        }
    }

@Suppress("unused")
val tweetInfoHook =
    bytecodePatch(
        description = "Hooks tweet info item",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val TWEETINFO_ENTRY_DESCRIPTOR = "$PATCHES_DESCRIPTOR/TweetInfo"

            val methods = tweetInfoHookFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $TWEETINFO_ENTRY_DESCRIPTOR;->checkEntry(Lcom/twitter/api/model/json/core/JsonApiTweet;)Lcom/twitter/api/model/json/core/JsonApiTweet;
                move-result-object p1
                """.trimIndent(),
            )
        }
    }
