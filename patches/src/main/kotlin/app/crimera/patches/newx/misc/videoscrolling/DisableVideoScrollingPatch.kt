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
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.crimera.patches.newx.utils.requireExactlyOne
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction23x
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION4_DESCRIPTOR = "Lkotlin/jvm/functions/Function4;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"

private const val PAGER_PARAMETER_PREFIX = "Landroidx/compose/foundation/pager/"
private const val LAYOUT_PARAMETER_PREFIX = "Landroidx/compose/foundation/layout/"
private const val UI_PARAMETER_PREFIX = "Landroidx/compose/ui/"
private const val SNAPPING_PARAMETER_PREFIX = "Landroidx/compose/foundation/gestures/snapping/"
private const val NESTED_SCROLL_PARAMETER_PREFIX = "Landroidx/compose/ui/input/nestedscroll/"
private const val FOUNDATION_PARAMETER_PREFIX = "Landroidx/compose/foundation/"

// R8 merges Compose's PagerKt into a repackaged library holder whose generated package and method
// name move between releases (12.28: Lcom/google/android/play/core/appupdate/b;, 12.29:
// Lcom/bumptech/glide/e;). Match the stable Compose ABI parameter roles and the non-obfuscated
// orientation enum instead of the holder's identity. Compose's pager ABI dropped its page-size
// float after 12.27, so accept both the 17- and 18-parameter arities. Prefix declarations use
// Morphe's STARTS_WITH parameter semantics; primitives stay exact.
private val VERTICAL_PAGER_PARAMETERS_17 =
    listOf(
        PAGER_PARAMETER_PREFIX,
        MODIFIER_DESCRIPTOR,
        LAYOUT_PARAMETER_PREFIX,
        PAGER_PARAMETER_PREFIX,
        "I",
        UI_PARAMETER_PREFIX,
        SNAPPING_PARAMETER_PREFIX,
        "Z",
        FUNCTION1_DESCRIPTOR,
        NESTED_SCROLL_PARAMETER_PREFIX,
        SNAPPING_PARAMETER_PREFIX,
        FOUNDATION_PARAMETER_PREFIX,
        FUNCTION4_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
        "I",
    )

private val VERTICAL_PAGER_PARAMETERS_18 =
    listOf(
        PAGER_PARAMETER_PREFIX,
        MODIFIER_DESCRIPTOR,
        LAYOUT_PARAMETER_PREFIX,
        PAGER_PARAMETER_PREFIX,
        "I",
        "F",
        UI_PARAMETER_PREFIX,
        SNAPPING_PARAMETER_PREFIX,
        "Z",
        FUNCTION1_DESCRIPTOR,
        NESTED_SCROLL_PARAMETER_PREFIX,
        SNAPPING_PARAMETER_PREFIX,
        FOUNDATION_PARAMETER_PREFIX,
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

private object VerticalPager17Fingerprint : Fingerprint(
    returnType = "V",
    parameters = VERTICAL_PAGER_PARAMETERS_17,
    custom = { method, _ -> isVerticalPagerMethod(method) },
)

private object VerticalPager18Fingerprint : Fingerprint(
    returnType = "V",
    parameters = VERTICAL_PAGER_PARAMETERS_18,
    custom = { method, _ -> isVerticalPagerMethod(method) },
)

private val verticalPagerFingerprints =
    listOf(VerticalPager17Fingerprint, VerticalPager18Fingerprint)

private fun patchVerticalPager(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val method = match.method
    if (method.instructions.isEmpty()) {
        throw PatchException("NewX VerticalPager target has no instructions: ${match.originalMethod}")
    }
    val p0Register = method.p0Register
    val booleanParamIndex = requireExactlyOne(
        "NewX VerticalPager userScrollEnabled parameter",
        method.parameterTypes.indices.filter { method.parameterTypes[it].toString() == "Z" },
    )
    val userScrollEnabledRegister = p0Register + booleanParamIndex
    val defaultMaskRegister = p0Register + method.parameterTypes.lastIndex
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

    // The old external label was anchored on the method's original first instruction, which is
    // exactly the instruction the injected read left behind its own block, so
    // `Target.Original` addresses it. The plain insertion kept incoming labels on that
    // instruction, so the branch policy stays `false`.
    method.insertHook(
        index = read.nextIndex,
        relocateBranchTargets = false,
    ) {
        ifEqz(read.register, Target.Original)
        // Clears `userScrollEnabled` and drops the `UserScrollEnabled` bit (0x100) from the
        // Compose default-parameter mask of the trailing parameters.
        constInt(userScrollEnabledRegister, 0)
        constInt(read.register, -0x101)
        add(
            BuilderInstruction23x(
                Opcode.AND_INT,
                defaultMaskRegister,
                defaultMaskRegister,
                read.register,
            ),
        )
    }
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
            val matches =
                verticalPagerFingerprints
                    .flatMap { fingerprint -> fingerprint.scopedMatchAllOrNull().orEmpty() }
                    .distinctBy { match -> match.originalMethod }
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX VerticalPager implementation, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            patchVerticalPager(matches.single(), disableVideoScrolling)
        }
    }
