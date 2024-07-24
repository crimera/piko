package crimera.patches.twitter.premium.unlockdownloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import app.revanced.patcher.extensions.or

//credits @revanced
object ImmersiveBottomSheetPatchFingerprint : MethodFingerprint(
    returnType = "V",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.CONSTRUCTOR,
    strings = listOf("captionsState")
)