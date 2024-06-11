package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object RecyclerViewGetCountFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT,
    returnType = "I",
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
)