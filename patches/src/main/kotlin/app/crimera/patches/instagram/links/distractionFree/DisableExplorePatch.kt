/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.returnOfflineRequestIf
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val disableExplorePatch =
    patch(
        name = "Disable explore",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val prefetch = Fingerprint(
                parameters = listOf(USER_SESSION_CLASS),
                returnType = "L",
                filters = listOf(
                    string("discover/topical_explore/"),
                    fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
                    fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
                ),
            ).match()
            val pathWrite = prefetch.instructionMatches[1]
            if (prefetch.instructionMatches.first().instruction.registersUsed.single() != pathWrite.instruction.registersUsed.first()) {
                throw PatchException("Explore prefetch does not write its endpoint")
            }
            val grid = Fingerprint(
                returnType = prefetch.originalMethod.returnType,
                filters = listOf(
                    fieldAccess(reference = pathWrite.fieldReference, opcode = Opcode.IPUT_OBJECT),
                    string("is_nonpersonalized_explore"),
                    string("ExploreTopicalFeedNetworkHelper"),
                ),
            ).match()
            // The regular grid factory also builds reshare suggestions. Test the actual path.
            for ((request, write) in listOf(prefetch to pathWrite, grid to grid.instructionMatches.first())) {
                val path = write.instruction.registersUsed.first()
                request.method.returnOfflineRequestIf(
                    write.index,
                    "invoke-static/range {v$path .. v$path}, $PREF_DESCRIPTOR->disableExplorePath(Ljava/lang/String;)Z",
                )
            }

            val recent = Fingerprint(
                parameters = listOf(USER_SESSION_CLASS),
                returnType = "V",
                filters = listOf(
                    fieldAccess(type = "Z", opcode = Opcode.SPUT_BOOLEAN),
                    string("fbsearch/recent_searches/"),
                    opcode(Opcode.RETURN_VOID),
                ),
            ).match()
            val pending = recent.instructionMatches.first()
            val markPending = recent.method.getInstruction(pending.index - 1)
            val flag = pending.instruction.registersUsed.single()
            if (markPending.opcode != Opcode.CONST_4 || markPending.registersUsed.single() != flag) {
                throw PatchException("Missing recent-search prefetch state")
            }
            // Skip only the REST prefetch, before it marks itself in flight.
            recent.method.addInstructionsWithLabels(
                pending.index - 1,
                """
                $PREF_CALL_DESCRIPTOR->disableExplore()Z
                move-result v$flag
                if-nez v$flag, :skip
                """.trimIndent(),
                ExternalLabel("skip", recent.method.getInstruction(recent.instructionMatches.last().index)),
            )
            enableSettings("disableExplore")
        }
    }
