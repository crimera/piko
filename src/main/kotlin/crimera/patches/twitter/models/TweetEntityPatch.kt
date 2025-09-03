package crimera.patches.twitter.models

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class TweetEntityPatch :
    BytecodePatch(
        setOf(
            TweetUsernameFingerprint,
            TweetMediaFingerprint,
            GetUserNameMethodCaller,
            TweetObjectFingerprint,
            TweetProfileNameFingerprint,
            TweetUserIdFingerprint,
            LongTweetObjectFingerprint,
            TweetShortTextFingerprint,
            TweetLongTextFingerprint,
            TweetMediaEntityClassFingerprint,
        ),
    ) {
    override fun execute(context: BytecodeContext) {
        val getUserNameMethodCaller =
            GetUserNameMethodCaller.result ?: throw PatchException("Could not find UserNameMethodCaller fingerprint")

        getUserNameMethodCaller.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str == "Name") {
                val methodName = getUserNameMethodCaller.getMethodName(match.index + 1)
                TweetUsernameFingerprint.changeFirstString(methodName)
            }
            if (str == "User Name") {
                val methodName = getUserNameMethodCaller.getMethodName(match.index + 1)
                TweetProfileNameFingerprint.changeFirstString(methodName)
            }
        }
// ------------
        val getTweetObjectResult = TweetObjectFingerprint.result ?: throw PatchException("getTweetObjectResult fingerprint not found")

        val tweetObjectClass = getTweetObjectResult.classDef

        val tweetObjectMethods = tweetObjectClass.methods

        val getTweetUserIdMethod =
            getTweetObjectResult.classDef.methods
                .last {
                    it.returnType == "J"
                }.name
        TweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)
// ------------
        val getMediaObjectMethod =
            tweetObjectMethods.firstOrNull { methodDef ->
                methodDef.implementation
                    ?.instructions
                    ?.map { it.opcode }
                    ?.toList() ==
                    listOf(
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.RETURN_OBJECT,
                    )
            } ?: throw PatchException("getMediaObject not found")
        TweetMediaFingerprint.changeFirstString(getMediaObjectMethod.name)

        val extMediaClassResult =
            TweetMediaEntityClassFingerprint.result ?: throw PatchException("TweetMediaEntityClassFingerprint not found")
        val extMediaListField =
            extMediaClassResult.classDef.fields
                .first { it.type.contains("List") }
                .name
        TweetMediaFingerprint.changeStringAt(1, extMediaListField)
// ------------
        val longTweetObjectResult =
            LongTweetObjectFingerprint.result ?: throw PatchException("Could not find LongTweetObjectFingerprint fingerprint")
        val getNoteTweetMethod =
            tweetObjectMethods
                .firstOrNull { it.returnType.contains("notetweet") }
                ?.name
                ?: throw PatchException("getNoteTweetMethod not found")
        TweetLongTextFingerprint.changeFirstString(getNoteTweetMethod)

        val longTextField =
            longTweetObjectResult.classDef.fields
                .first { it.type == "Ljava/lang/String;" }
                .name
        TweetLongTextFingerprint.changeStringAt(1, longTextField)

// ---------------------
        val tweetEntityMethod =
            tweetObjectMethods
                .lastOrNull { it.returnType.contains("/entity/") }
                ?.name
                ?: throw PatchException("getTweetEntityMethod not found")
        TweetShortTextFingerprint.changeFirstString(tweetEntityMethod)

// End
// ---------------------
    }
}
