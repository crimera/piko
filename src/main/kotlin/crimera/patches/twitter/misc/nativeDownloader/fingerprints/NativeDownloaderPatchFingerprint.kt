package crimera.patches.twitter.misc.nativeDownloader.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object NativeDownloaderPatchFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "sandbox://tweetview?id="
    ),
)