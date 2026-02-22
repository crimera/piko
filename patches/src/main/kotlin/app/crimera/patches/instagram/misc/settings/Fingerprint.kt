package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.shared.misc.extension.EXTENSION_CLASS_DESCRIPTOR
import com.android.tools.smali.dexlib2.AccessFlags

internal object AddButtonOnProfileBarFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/profile/actionbar/ProfileActionBar;",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

internal object OnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/mainactivity/LauncherActivity;",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    definingClass = Constants.ACTIVITY_SETTINGS_STATUS_CLASS,
    name = "load",
)

internal object ExtensionsUtilsFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "load",
)
