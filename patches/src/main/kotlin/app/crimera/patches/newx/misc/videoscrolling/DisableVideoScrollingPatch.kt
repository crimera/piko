/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.videoscrolling

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION4_DESCRIPTOR = "Lkotlin/jvm/functions/Function4;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"

private const val VERTICAL_PAGER_OWNER_SCOPE = "Lcom/google/android/play/core/appupdate/"

// R8 merges Compose PagerKt into a repackaged library holder. Match the stable Compose ABI and
// the non-obfuscated orientation enum instead of the holder's generated class and method names.
private val VERTICAL_PAGER_PARAMETERS =
    listOf(
        "L",
        MODIFIER_DESCRIPTOR,
        "L",
        "L",
        "I",
        "F",
        "L",
        "L",
        "Z",
        FUNCTION1_DESCRIPTOR,
        "L",
        "L",
        "L",
        FUNCTION4_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
        "I",
    )

private fun isVerticalPagerMethod(method: Method): Boolean =
    method.implementation?.instructions?.any { instruction ->
        if (instruction.opcode != Opcode.SGET_OBJECT) return@any false
        val field = instruction.getReference<FieldReference>() ?: return@any false
        field.name == "Vertical" &&
            field.definingClass.startsWith("Landroidx/compose/foundation/gestures/")
    } == true

private object VerticalPagerFingerprint : Fingerprint(
    definingClass = VERTICAL_PAGER_OWNER_SCOPE,
    returnType = "V",
    parameters = VERTICAL_PAGER_PARAMETERS,
    custom = { method, _ -> isVerticalPagerMethod(method) },
)

private fun patchVerticalPager(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val method = match.method
    val originalFirstInstruction =
        method.instructions.firstOrNull()
            ?: throw PatchException("NewX VerticalPager target has no instructions: ${match.originalMethod}")
    val p0Register = method.p0Register
    val userScrollEnabledRegister = p0Register + 8
    val defaultMaskRegister = p0Register + 17
    if (userScrollEnabledRegister > 255 || defaultMaskRegister > 255) {
        throw PatchException(
            "NewX VerticalPager parameter registers exceed bytecode encoding limits: " +
                "userScrollEnabled=v$userScrollEnabledRegister, defaultMask=v$defaultMaskRegister",
        )
    }

    val read =
        setting.injectRead(
            method = method,
            index = 0,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    if (read.register == userScrollEnabledRegister || read.register == defaultMaskRegister) {
        throw PatchException(
            "NewX VerticalPager setting register aliases a parameter: v${read.register}",
        )
    }

    method.addInstructionsWithLabels(
        read.nextIndex,
        """
            if-eqz v${read.register}, :piko_newx_video_scrolling_continue
            const/16 v$userScrollEnabledRegister, 0x0
            const/16 v${read.register}, -0x101
            and-int v$defaultMaskRegister, v$defaultMaskRegister, v${read.register}
        """.trimIndent(),
        ExternalLabel(
            "piko_newx_video_scrolling_continue",
            originalFirstInstruction,
        ),
    )
}

@Suppress("unused")
val newXDisableVideoScrollingPatch =
    bytecodePatch(
        name = "NewX: Disable video player scrolling",
        description =
            "Disables vertical swipes in the NewX video player while keeping playback controls and other gestures available.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val disableVideoScrolling =
            newXToggle(
                id = "newx.post_actions_media.disable_video_scrolling",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_disable_video_scrolling"),
                order = 101,
                defaultValue = false,
            )

        execute {
            val matches = VerticalPagerFingerprint.scopedMatchAllOrNull().orEmpty()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX VerticalPager implementation, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            patchVerticalPager(matches.single(), disableVideoScrolling)
        }
    }
