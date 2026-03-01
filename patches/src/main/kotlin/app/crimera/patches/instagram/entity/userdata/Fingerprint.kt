package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/UserData;"
internal object GetAdditionalUserInfoExtensionFingerprint: Fingerprint (
    name = "getAdditionalUserInfo",
    definingClass = EXTENSION_CLASS_DESCRIPTOR
)

internal object GetUsernameExtensionFingerprint: Fingerprint (
    name = "getUsername",
    definingClass = EXTENSION_CLASS_DESCRIPTOR
)

internal object GetFullNameExtensionFingerprint: Fingerprint (
    name = "getFullname",
    definingClass = EXTENSION_CLASS_DESCRIPTOR
)

internal object GetMatrixCursorFingerprint: Fingerprint (
    returnType = "Landroid/database/MatrixCursor;",
    definingClass = "Lcom/instagram/contentprovider/FamilyAppsUserValuesProvider;",
)