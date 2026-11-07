/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.dialogbox

import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch

val instagramDialogBoxEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the native box of Instagram",
    ) {
        execute {
            ConstructorExtensionFingerprint.changeFirstString(
                classNameToExtension(GetDialogFingerprint.originalClassDef.type),
            )

            GetDialogExtensionFingerprint.changeFirstString(GetDialogFingerprint.originalMethod.name)

            ShowDialogHelperFingerprint.instructionMatches.let {
                SetTitleExtensionFingerprint.changeFirstString(it[0].fieldReference.name)
                SetMessageExtensionFingerprint.changeFirstString(it[1].methodReference.name)
                SetCancelableExtensionFingerprint.changeFirstString(it[3].methodReference.name)
                SetCanceledOnTouchOutsideExtensionFingerprint.changeFirstString(it[5].methodReference.name)
                SetNegativeButtonExtensionFingerprint.changeFirstString(it[7].methodReference.name)
                SetPositiveButtonExtensionFingerprint.changeFirstString(it[9].methodReference.name)
                SetOnDismissListenerExtensionFingerprint.changeFirstString(it[10].methodReference.name)
            }

            AddDialogMenuItemsExtensionFingerprint.changeFirstString(AddDialogMenuItemsFingerprint.originalMethod.name)

            ClearMenuBottomCornersExtensionFingerprint.changeFirstString(
                GetDialogFingerprint.instructionMatches.last().fieldReference.name,
            )
        }
    }
