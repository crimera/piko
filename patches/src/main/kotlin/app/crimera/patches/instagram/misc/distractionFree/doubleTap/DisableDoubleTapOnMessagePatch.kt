/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

internal const val SIMPLE_ON_GESTURE_LISTENER_CLASS = "Landroid/view/GestureDetector\$SimpleOnGestureListener;"

internal object MessageGestureDetectorInitFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(SIMPLE_ON_GESTURE_LISTENER_CLASS, "Landroid/view/View;", "Landroid/widget/TextView;", "Z"),
    custom = { _, classDef ->
        classDef.superclass == SIMPLE_ON_GESTURE_LISTENER_CLASS
    },
)

internal object MessageGestureOnDoubleTapFingerprint : Fingerprint(
    classFingerprint = MessageGestureDetectorInitFingerprint,
    name = "onDoubleTap",
)

@Suppress("unused")
val disableDoubleTapOnMessagePatch =
    bytecodePatch(
        description = "Disable double tap like on messages",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            MessageGestureOnDoubleTapFingerprint.method
                .addInstructions(
                    0,
                    DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapMessage"),
                )
        }
    }
