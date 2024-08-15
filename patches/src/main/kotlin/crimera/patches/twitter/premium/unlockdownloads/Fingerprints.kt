package crimera.patches.twitter.premium.unlockdownloads

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal val downloadPatchFingerprint = fingerprint {
    returns("V")
    strings(
        "media_options_sheet",
        "resources.getString(R.string.post_video)",
        "resources.getString(R.string.post_photo)"
    )
}
internal val fileDownloaderFingerprint = fingerprint {
    returns("Z")
    strings(
        "mediaEntity",
        "variantToDownload.url"
    )
}

internal val mediaEntityFingerprint = fingerprint {
    opcodes(Opcode.IGET_BOOLEAN)
    custom { method, _ ->
        method.definingClass == "Lcom/twitter/model/json/core/JsonMediaEntity;"
    }
}

//credits @revanced
internal val immersiveBottomSheetPatchFingerprint = fingerprint {
    returns("V")
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    strings("captionsState")
}
