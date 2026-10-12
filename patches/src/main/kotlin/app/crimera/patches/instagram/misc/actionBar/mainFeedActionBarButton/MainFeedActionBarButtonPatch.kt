/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

object BindMainFeedActionBarFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("BindMainFeedActionBar"),
        // Action button container.
        fieldAccess(
            definingClass = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;",
            type = "Landroid/widget/LinearLayout;",
            opcode = Opcode.IGET_OBJECT,
        ),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(name = "setVisibility", parameters = listOf("I"), returnType = "V", location = MatchAfterImmediately()),
    ),
)

private object HomeActionModelFingerprint : Fingerprint(
    name = "<init>",
    strings = listOf("share", "news", "quick_snap", "manage_feeds"),
    filters = listOf(
        // Home action name.
        methodCall(parameters = listOf(USER_SESSION_CLASS, "I"), returnType = "Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    ),
)

val hideHomeActionButtonsPatch = bytecodePatch {
    execute {
        val match = HomeActionModelFingerprint.matchSingle()
        val method = match.method
        val nullCheckIndex = match.instructionMatches.last().index
        val register = match.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
        method.addInstructions(
            nullCheckIndex,
            """
            invoke-static/range {v$register .. v$register}, $ACTIONBAR_DESCRIPTOR->filterHomeAction(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$register
            """.trimIndent(),
        )
    }
}

val mainFeedActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            BindMainFeedActionBarFingerprint.matchSingle().let {
                val containerIndex = it.instructionMatches[1].index
                val layoutRegister = it.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstruction(
                    containerIndex + 1,
                    "invoke-static/range {v$layoutRegister .. v$layoutRegister}, $ACTIONBAR_DESCRIPTOR->mainFeedActionBarButton(Landroid/view/ViewGroup;)V",
                )
            }
            addFlags("mainFeedActionBarFlags")
        }
    }
