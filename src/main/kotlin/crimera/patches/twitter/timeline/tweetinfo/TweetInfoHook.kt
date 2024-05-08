package crimera.patches.twitter.timeline.tweetinfo


import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch

object TweetInfoHookFingerprint:MethodFingerprint(
    returnType = "Ljava/lang/Object",
    customFingerprint = {it,_->
        it.definingClass == "Lcom/twitter/api/model/json/core/JsonApiTweet\$\$JsonObjectMapper;" && it.name == "parse"
    }
)

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
    requiresIntegrations = true
)
object TweetInfoHook:BytecodePatch(
    setOf(TweetInfoHookFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val TWEETINFO_ENTRY_DESCRIPTOR = "${SettingsPatch.PATCHES_DESCRIPTOR}/TweetInfo"

        val result = TweetInfoHookFingerprint.result
            ?:throw PatchException("TweetInfoHookFingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(returnObj,"""
        invoke-static {p1}, $TWEETINFO_ENTRY_DESCRIPTOR;->checkEntry(Lcom/twitter/api/model/json/core/JsonApiTweet;)Lcom/twitter/api/model/json/core/JsonApiTweet;
        move-result-object p1
        """.trimIndent())

        //end
    }
}