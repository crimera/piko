/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
                addInstructions(
                    0,
                    """
                    iget-object v0, p0, $EXTENSION_CLASS_DESCRIPTOR->igdsButton:$IGDS_BUTTON_CLASS_DESCRIPTOR
                    invoke-virtual {v0, p1}, $IGDS_BUTTON_CLASS_DESCRIPTOR->setStyle($buttonStyleClass)V
                    """.trimIndent(),
                )
            }
        }
    }
