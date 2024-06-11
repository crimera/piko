package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object GetCountFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT,
    returnType = "I",
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
)

object OnCreateViewHolderFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT,
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$c0;",
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
)

object OnBindViewHolderFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT,
    returnType = "V",
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Landroidx/recyclerview/widget/RecyclerView\$e;")
    }
)