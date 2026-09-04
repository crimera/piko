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
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FOLLOWER_TIMELINE_STACK_FLAG =
    "android_follower_timelines_stack_enabled"

private object TimelineUserActionFactoryFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    custom = { methodDef, classDef ->
        val parameters = methodDef.parameters

        classDef.type.startsWith("Lcom/twitter/app/common/timeline/di/view/") &&
            methodDef.returnType.startsWith("Lcom/twitter/users/timeline/") &&
            parameters.size == 11 &&
            parameters[0].type == "Landroid/content/Context;" &&
            parameters[1].type == "Landroidx/fragment/app/FragmentManager;" &&
            parameters[4].type == "Lcom/twitter/util/user/UserIdentifier;" &&
            parameters[8].type == "Landroidx/fragment/app/Fragment;" &&
            methodDef.implementation?.instructions?.any { instruction ->
                instruction.getReference<FieldReference>()?.type ==
                    "Lcom/twitter/util/user/UserIdentifier;"
            } == true
    },
)

private object UserTimelineNavigationFingerprint : Fingerprint(
    strings = listOf(FOLLOWER_TIMELINE_STACK_FLAG),
    custom = { _, classDef ->
        classDef.type.startsWith("Lcom/twitter/users/timeline/")
    },
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

            TimelineUserActionFactoryFingerprint.matchAll(2..2).forEach { match ->
                match.method.addInstructions(0, USE_CURRENT_USER.trimIndent())
            }
        }
    }
