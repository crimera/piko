/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.fixListRelationshipActions

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FOLLOWER_TIMELINE_STACK_FLAG =
    "android_follower_timelines_stack_enabled"

private const val TIMELINE_USER_ACTION_FACTORY =
    "Lcom/twitter/app/common/timeline/di/view/l;"

private const val TIMELINE_USER_ACTION_FACTORY_ALTERNATE =
    "Lcom/twitter/app/common/timeline/di/view/i0;"

private const val TIMELINE_USER_ACTION_HANDLER =
    "Lcom/twitter/users/timeline/l;"

private val timelineUserActionFactoryParameters =
    listOf(
        "Landroid/content/Context;",
        "Landroidx/fragment/app/FragmentManager;",
        "Lcom/twitter/safetymode/common/h;",
        "Lcom/twitter/async/http/f;",
        "Lcom/twitter/util/user/UserIdentifier;",
        "Lcom/twitter/cache/twitteruser/a;",
        "Lcom/twitter/app/common/z;",
        "Lcom/twitter/analytics/feature/model/p1;",
        "Landroidx/fragment/app/Fragment;",
        "Lcom/twitter/onboarding/gating/a;",
        "Lcom/twitter/onboarding/gating/c;",
    )

private object TimelineUserActionFactoryFingerprint : Fingerprint(
    definingClass = TIMELINE_USER_ACTION_FACTORY,
    parameters = timelineUserActionFactoryParameters,
    returnType = TIMELINE_USER_ACTION_HANDLER,
)

private object TimelineUserActionFactoryAlternateFingerprint : Fingerprint(
    definingClass = TIMELINE_USER_ACTION_FACTORY_ALTERNATE,
    parameters = timelineUserActionFactoryParameters,
    returnType = TIMELINE_USER_ACTION_HANDLER,
)

private object UserTimelineNavigationFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/users/timeline/b;",
    strings = listOf(FOLLOWER_TIMELINE_STACK_FLAG),
)

private object ProfileHeaderRelationshipNavigationFingerprint : Fingerprint(
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    strings = listOf(FOLLOWER_TIMELINE_STACK_FLAG),
)

private const val USE_CURRENT_USER =
    """
    invoke-static {}, Lcom/twitter/util/user/UserIdentifier;->getCurrent()Lcom/twitter/util/user/UserIdentifier;
    move-result-object p4
    """

/**
 * X 12.7 introduced an XLite relationship-list route whose inline follow/unfollow actions can
 * revert without changing the server relationship. Route only these lists through the proven
 * legacy implementation and ensure its action handler uses the active account identifier.
 */
@Suppress("unused")
val fixListRelationshipActionsPatch =
    bytecodePatch(
        name = "Fix list relationship actions",
        description = "Fixes follow and unfollow buttons in user lists.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        execute {
            val navigationMethods =
                UserTimelineNavigationFingerprint.classDef.methods.filter { method ->
                    method.implementation?.instructions?.any { instruction ->
                        instruction.getReference<StringReference>()?.string ==
                            FOLLOWER_TIMELINE_STACK_FLAG
                    } == true
                } + ProfileHeaderRelationshipNavigationFingerprint.method

            navigationMethods.distinct().forEach { method ->
                val flagIndex =
                    method.instructions
                        .first {
                            it.getReference<StringReference>()?.string ==
                                FOLLOWER_TIMELINE_STACK_FLAG
                        }.location.index
                val resultIndex =
                    method.instructions
                        .drop(flagIndex + 1)
                        .first { it.opcode == Opcode.MOVE_RESULT }
                        .location.index
                val resultRegister =
                    method.getInstruction<OneRegisterInstruction>(resultIndex).registerA

                method.addInstructions(resultIndex + 1, "const/4 v$resultRegister, 0x0")
            }

            listOf(
                TimelineUserActionFactoryFingerprint,
                TimelineUserActionFactoryAlternateFingerprint,
            ).forEach { fingerprint ->
                fingerprint.method.addInstructions(0, USE_CURRENT_USER.trimIndent())
            }
        }
    }
