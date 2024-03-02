package crimera.patches.twitter.timeline.banner.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object HideBannerFingerprint : MethodFingerprint(
    returnType = "Z",
    opcodes = listOf( Opcode.RETURN),
    customFingerprint = {it, _ ->
        it.definingClass == "Lcom/twitter/timeline/newtweetsbanner/BaseNewTweetsBannerPresenter;"
    }
)