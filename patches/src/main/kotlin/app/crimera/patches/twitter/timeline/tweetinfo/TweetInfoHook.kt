package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object TweetInfoHookFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/api/model/json/core/JsonApiTweet\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

val tweetInfoHook =
    bytecodePatch(
        description = "Hooks tweet info item",
    ) {
        dependsOn(settingsPatch)

        execute {
            val TWEETINFO_ENTRY_DESCRIPTOR = "$PATCHES_DESCRIPTOR/TweetInfo"

            val methods = TweetInfoHookFingerprint.method
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
