/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.dialogbox

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/InstagramDialogBox;"
internal object GetDialogFingerprint : Fingerprint(
    returnType = "Landroid/app/Dialog;",
    strings = listOf("DialogBuilder - Activity is finishing"),
    filters = listOf(
        // The native builder enables top and bottom rounding together on the menu adapter.
        opcode(Opcode.CONST_4),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
    ),
)

internal object AddDialogMenuItemsFingerprint : Fingerprint(
    classFingerprint = GetDialogFingerprint,
    returnType = "V",
    parameters = listOf("Landroid/content/DialogInterface\$OnClickListener;", "[Ljava/lang/CharSequence;"),
)

/**
 * Builds a native dialog with the dialog builder setters.
 */
internal object ShowDialogHelperFingerprint : Fingerprint(
    name = "showDialogHelper",
    definingClass = "Lcom/instagram/react/modules/base/IgReactDialogModule;",
    filters = listOf(
        // 0: Title.
        fieldAccess(opcode = Opcode.IPUT_OBJECT),
        // 1: Message.
        methodCall(parameters = listOf("Ljava/lang/CharSequence;"), opcode = Opcode.INVOKE_VIRTUAL),
        // 2: Cancelable.
        string("IS_CANCELABLE"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL),
        // 4: Canceled on touch outside.
        string("CANCELED_ON_TOUCH_OUTSIDE"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL),
        // 6: Negative button.
        string("NEGATIVE_BUTTON_TEXT"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL),
        // 8: Positive button.
        string("POSITIVE_BUTTON_TEXT"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL),
        // 10: On dismiss listener.
        methodCall(
            parameters = listOf("Landroid/content/DialogInterface\$OnDismissListener;"),
            location = MatchAfterImmediately(),
        ),
    ),
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

internal object ClearMenuBottomCornersExtensionFingerprint : Fingerprint(
    name = "clearMenuBottomCorners",
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
