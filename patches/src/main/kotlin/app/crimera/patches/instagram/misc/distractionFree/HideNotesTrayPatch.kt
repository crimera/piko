/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object NotesTrayUpdateFingerprint : Fingerprint(
    name = "emit",
    filters = listOf(
        string("null cannot be cast to non-null type instagram.features.direct.inbox.notes.tray.presentation.controller.Ig4aDirectNotesTrayController.NotesTrayHeaderDelegateImpl"),
        methodCall(smali = "Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;"),
    ),
)

private object FriendAppsRegistrationsFingerprint : Fingerprint(
    name = "invoke",
    filters = listOf(
        string("Notes Friend App registrations unavailable; using legacy tray"),
        opcode(Opcode.RETURN_OBJECT),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("Ljava/lang/Iterable;"), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val hideNotesTrayPatch = patch(
    name = "Hide notes tray",
    description = "Hides notes tray in DM section",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val update = NotesTrayUpdateFingerprint.match()
        val publish = update.instructionMatches.last()
        val (callback, model) = publish.instruction.registersUsed
        val scratch = update.method.findFreeRegister(publish.index, listOf(callback, model))

        // A null model removes the row before either the RecyclerView or Compose renderer runs.
        update.method.addInstructionsWithLabels(
            publish.index,
            """
            $PREF_CALL_DESCRIPTOR->hideNotesTray()Z
            move-result v$scratch
            if-eqz v$scratch, :publish
            const/16 v$model, 0
            """.trimIndent(),
            ExternalLabel("publish", update.method.getInstruction(publish.index)),
        )

        val friendApps = FriendAppsRegistrationsFingerprint.match()
        val builder = friendApps.instructionMatches.last()
        val registrations = builder.instruction.registersUsed.single()
        val nextIndex = builder.index + 1
        val check = friendApps.method.findFreeRegister(nextIndex, listOf(registrations))

        // Only note creation and consumption are registered so far. Keep the native fallback
        // for unavailable registrations and let Instagram append the other Friend Apps.
        friendApps.method.addInstructionsWithLabels(
            nextIndex,
            """
            $PREF_CALL_DESCRIPTOR->hideNotesTray()Z
            move-result v$check
            if-eqz v$check, :other_apps
            invoke-interface/range {v$registrations .. v$registrations}, Ljava/util/List;->clear()V
            """.trimIndent(),
            ExternalLabel("other_apps", friendApps.method.getInstruction(nextIndex)),
        )
        enableSettings("hideNotesTray")
    }
}
