/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.instagramButton

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

val instagramButtonEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of Instagram button",
    ) {
        execute {
            val buttonStyleClass =
                IgdsButtonSetStyleFingerprint.method.parameters
                    .first()
                    .type

            SetStyleExtensionFingerprint.changeFirstString(classNameToExtension(buttonStyleClass))

            SetStyleObjectExtensionFingerprint.method.apply {
                // p1 arrives typed as java.lang.Object (the setStyleObject(Object)
                // signature is intentionally generic so callers don't depend on
                // the obfuscated IgdsButtonStyle class name). ART's verifier
                // requires an explicit check-cast before we hand p1 to
                // setStyle($buttonStyleClass), otherwise the class fails to
                // verify with "register v1 has type Object but expected …".
                addInstructions(
                    0,
                    """
                    iget-object v0, p0, $EXTENSION_CLASS_DESCRIPTOR->igdsButton:$IGDS_BUTTON_CLASS_DESCRIPTOR
                    check-cast p1, $buttonStyleClass
                    invoke-virtual {v0, p1}, $IGDS_BUTTON_CLASS_DESCRIPTOR->setStyle($buttonStyleClass)V
                    """.trimIndent(),
                )
            }
        }
    }
