package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"

private object NewXNewPostButtonFingerprint : Fingerprint(
    definingClass = "Lcom/x/ui/common/",
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
        name = "NewX: Hide compose button",
        description = "Removes the compose button from NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hideNewPostButton =
            newXToggle(
                id = "newx.timeline.hide_new_post_button",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_new_post_button"),
                order = 300,
                defaultValue = false,
            )

        execute {
            val matches = NewXNewPostButtonFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX new-post button renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hideNewPostButton.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
