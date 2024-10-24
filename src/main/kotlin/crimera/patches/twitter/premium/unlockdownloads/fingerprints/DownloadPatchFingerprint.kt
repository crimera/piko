package crimera.patches.twitter.premium.unlockdownloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

internal object DownloadPatchFingerprint : MethodFingerprint(
    opcodes = listOf(
        Opcode.IF_EQ,
        Opcode.SGET_OBJECT,
        Opcode.GOTO_16,
        Opcode.NEW_INSTANCE,
    ),
    strings = listOf(
        "mediaEntity",
        "media_options_sheet",
    )
)