package crimera.patches.twitter.models

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class TweetInfoEntityPatch :
    BytecodePatch(
        setOf(
            TweetInfoObjectFingerprint,
            TweetLangFingerprint,
            TweetInfoFingerprint,
            TweetObjectFingerprint,
        ),
    ) {
    override fun execute(context: BytecodeContext) {
        val tweetInfoObjectResult =
            TweetInfoObjectFingerprint.result ?: throw PatchException("Could not find TweetInfoObjectFingerprint fingerprint")

        
// ------------
        val tweetObjectResult =
            TweetObjectFingerprint.result ?: throw PatchException("Could not find TweetObjectFingerprint fingerprint")

        tweetInfoObjectResult.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str == "lang") {
                val ref = tweetInfoObjectResult.getReference(match.index + 1) as FieldReference
                TweetLangFingerprint.changeFirstString(ref.name)

                var infoField = tweetObjectResult.classDef.fields
                    .first {
                        it.type ==
                            ref.definingClass
                    }.name
                TweetInfoFingerprint.changeFirstString(infoField)
                return
            }
        }

// ------------
    }
}
