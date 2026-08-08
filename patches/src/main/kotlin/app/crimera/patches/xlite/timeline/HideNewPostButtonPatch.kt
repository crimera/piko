package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.returnVoidIfEnabled
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"

private object XLiteNewPostButtonFingerprint : Fingerprint(
    parameters =
        listOf(
            "I",
            COMPOSER_DESCRIPTOR,
            MODIFIER_DESCRIPTOR,
            FUNCTION_ZERO_DESCRIPTOR,
        ),
    returnType = "V",
    custom = { _, classDef ->
        val packageRelativeName = classDef.type.removePrefix("Lcom/x/ui/common/")
        classDef.type.startsWith("Lcom/x/ui/common/") && !packageRelativeName.contains('/')
    },
    filters =
        listOf(
            string("onClick"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "isVisible",
                parameters = listOf(),
                returnType = "Z",
            ),
        ),
)

@Suppress("unused")
val hideNewPostButtonPatch =
    bytecodePatch(
        name = "X-Lite: Hide compose button",
        description = "Removes the compose button from X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hideNewPostButton =
            xLiteToggle(
                id = "xlite.timeline.hide_new_post_button",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_xlite_hide_new_post_button"),
                order = 250,
                defaultValue = false,
            )

        execute {
            val matches = XLiteNewPostButtonFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite new-post button renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hideNewPostButton.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
