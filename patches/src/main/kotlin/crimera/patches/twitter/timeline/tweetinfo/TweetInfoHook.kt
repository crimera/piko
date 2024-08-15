package crimera.patches.twitter.timeline.tweetinfo


import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR

internal val tweetInfoHookFingerprint = fingerprint {
    returns("Ljava/lang/Object")
    custom { it, _ ->
        it.definingClass == "Lcom/twitter/api/model/json/core/JsonApiTweet\$\$JsonObjectMapper;"
                && it.name == "parse"
    }
}

// TODO: separate integrations
@Suppress("unused")
val tweetInfoHook = bytecodePatch {
    compatibleWith("com.twitter.android")

    val result by tweetInfoHookFingerprint()

    execute {
        val TWEETINFO_ENTRY_DESCRIPTOR = "${PATCHES_DESCRIPTOR}/TweetInfo"

        val methods = result.mutableMethod
        val instructions = methods.instructions

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(
            returnObj, """
        invoke-static {p1}, $TWEETINFO_ENTRY_DESCRIPTOR;->checkEntry(Lcom/twitter/api/model/json/core/JsonApiTweet;)Lcom/twitter/api/model/json/core/JsonApiTweet;
        move-result-object p1
        """.trimIndent()
        )
    }
}