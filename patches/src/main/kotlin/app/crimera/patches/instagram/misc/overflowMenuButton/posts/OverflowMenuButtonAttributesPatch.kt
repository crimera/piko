/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.MEDIA_OPTIONS_CLASS
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode

context(patchContext: BytecodePatchContext)
fun addOverflowMenuButtonAttributes(
    buttonEnumTag: String,
    extensionMethodName: String,
) {
    OverflowMenuButtonEnumInitialiser.apply {
        val classFields = classDef.fields
        val field =
            classFields
                .first { it.type == MEDIA_OPTIONS_CLASS }
                .toMutable()
        field.name = buttonEnumTag
        classFields.add(field)

        method.apply {
            // After the last native option is initialized and stored.
            val lastInvokeDirectIndex = indexOfFirstInstructionReversedOrThrow(
                methodCall(definingClass = MEDIA_OPTIONS_CLASS, name = "<init>", opcode = Opcode.INVOKE_DIRECT),
            )

            addInstructions(
                lastInvokeDirectIndex + 2,
                """
                invoke-static {}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->$extensionMethodName()$MEDIA_OPTIONS_CLASS
                move-result-object v0
                sput-object v0, $MEDIA_OPTIONS_CLASS->$buttonEnumTag:$MEDIA_OPTIONS_CLASS
                """.trimIndent(),
            )
        }
    }
}
