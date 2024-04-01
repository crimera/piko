package crimera.patches.twitter.link.customsharingdomain.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object CustomSharingDomainFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    strings = listOf(
        "res.getString(R.string.t…lUsername, id.toString())"
    )
)