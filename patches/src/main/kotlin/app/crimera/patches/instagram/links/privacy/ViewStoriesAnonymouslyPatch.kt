/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object StorySeenUriBuilderFingerprint : Fingerprint(
    filters =
        listOf(
            string("media/seen/"),
        ),
)

val viewStoriesAnonymouslyPatch =
    bytecodePatch(
        name = "View stories anonymously",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            StorySeenUriBuilderFingerprint.classDef.methods.lastOrNull { it.returnType == "Z" }?.apply {
                val lastIfEqz = instructions.lastOrNull { it.opcode == Opcode.IF_EQZ }
                    ?: throw PatchException("Failed to find IF_EQZ in StorySeenUriBuilderFingerprint")

                replaceInstruction(
                    lastIfEqz.location.index,
                    "invoke-static {}, Lapp/crimera/piko/patches/instagram/PrivacyPatch;->viewStoriesAnonymously()Z",
                    "move-result v0",
                    "if-nez v0, :cond_0",
                    "const/4 v0, 0x0",
                    "return v0",
                    ":cond_0",
                )
            } ?: throw PatchException("Failed to find boolean method in ${StorySeenUriBuilderFingerprint.definingClass}")
        }
    }
