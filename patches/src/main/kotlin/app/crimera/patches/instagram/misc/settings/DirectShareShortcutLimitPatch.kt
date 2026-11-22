/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.ACTIVITY_SETTINGS_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.registersUsed

private const val SHORTCUT_MANAGER = "Landroid/content/pm/ShortcutManager;"
private const val MAX_SHORTCUTS = "$SHORTCUT_MANAGER->getMaxShortcutCountPerActivity()I"

private object DirectShareCandidatesFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(smali = MAX_SHORTCUTS),
        string("max allowed"),
    ),
)

private object PublishShortcutFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(smali = MAX_SHORTCUTS),
        methodCall(smali = "$SHORTCUT_MANAGER->getDynamicShortcuts()Ljava/util/List;"),
        methodCall(smali = "$SHORTCUT_MANAGER->addDynamicShortcuts(Ljava/util/List;)Z"),
    ),
)

internal val directShareShortcutLimitPatch = patch {
    dependsOn(sharedExtensionPatch)
    execute {
        for (fingerprint in listOf(DirectShareCandidatesFingerprint, PublishShortcutFingerprint)) {
            val match = fingerprint.match()
            val limit = match.instructionMatches.first()
            val manager = limit.instruction.registersUsed.single()
            match.method.replaceInstruction(
                limit.index,
                "invoke-static/range {v$manager .. v$manager}, " +
                    "$ACTIVITY_SETTINGS_CLASS/ShortcutLimit;->getDynamicLimit($SHORTCUT_MANAGER)I",
            )
        }
    }
}
