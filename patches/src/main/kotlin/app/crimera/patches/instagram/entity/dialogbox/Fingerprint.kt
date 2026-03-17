/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.dialogbox

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/InstagramDialogBox;"
internal val TARGET_STRING_ARRAY =
    arrayOf(
        "IS_CANCELABLE",
        "CANCELED_ON_TOUCH_OUTSIDE",
        "NEGATIVE_BUTTON_TEXT",
        "POSITIVE_BUTTON_TEXT",
    )

internal object GetDialogFingerprint : Fingerprint(
    returnType = "Landroid/app/Dialog;",
    strings = listOf("DialogBuilder - Activity is finishing"),
)

internal object ShowDialogHelperFingerprint : Fingerprint(
    strings = listOf(*TARGET_STRING_ARRAY),
    name = "showDialogHelper",
    definingClass = "Lcom/instagram/react/modules/base/IgReactDialogModule;",
)

internal object OpenNativePhotoPickerFingerprint : Fingerprint(
    name = "openNativePhotoPicker",
    definingClass = "Lcom/instagram/react/modules/product/IgReactMediaPickerNativeModule;",
)

internal object ConstructorExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object AddDialogMenuItemsExtensionFingerprint : Fingerprint(
    name = "addDialogMenuItems",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetDialogExtensionFingerprint : Fingerprint(
    name = "getDialog",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetCancelableExtensionFingerprint : Fingerprint(
    name = "setCancelable",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetCanceledOnTouchOutsideExtensionFingerprint : Fingerprint(
    name = "setCanceledOnTouchOutside",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetMessageExtensionFingerprint : Fingerprint(
    name = "setMessage",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetNegativeButtonExtensionFingerprint : Fingerprint(
    name = "setNegativeButton",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetOnDismissListenerExtensionFingerprint : Fingerprint(
    name = "setOnDismissListener",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetPositiveButtonExtensionFingerprint : Fingerprint(
    name = "setPositiveButton",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object SetTitleExtensionFingerprint : Fingerprint(
    name = "setTitle",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal val TARGET_FINGERPRINT_ARRAY =
    arrayOf(
        SetCancelableExtensionFingerprint,
        SetCanceledOnTouchOutsideExtensionFingerprint,
        SetNegativeButtonExtensionFingerprint,
        SetPositiveButtonExtensionFingerprint,
    )
