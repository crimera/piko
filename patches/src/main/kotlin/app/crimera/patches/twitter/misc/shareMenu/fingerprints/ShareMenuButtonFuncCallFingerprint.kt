/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.crimera.utils.changeFirstString
import app.crimera.utils.methodExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val shareMenuButtonFuncCallFingerprint =
    bytecodePatch(
        description = "For share menu button function call reflection",
    ) {
        execute {
            ShareMenuButtonFuncCallFingerprint.apply {
                val match = stringMatches.firstOrNull { it.string == "Delete Status" }
                    ?: throw PatchException("Failed to find 'Delete Status' string in ShareMenuButtonFuncCallFingerprint")
                val strIndex = match.index

                method.apply {
                    val invokeVirtualIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)
                    if (invokeVirtualIndex < 0) throw PatchException("Failed to find INVOKE_VIRTUAL after string in ShareMenuButtonFuncCallFingerprint")
                    val invokeVirtualMatch = getInstruction(invokeVirtualIndex)
                    ShareMenuButtonFuncCallExtensionFingerprint.changeFirstString(invokeVirtualMatch.methodExtractor().name)
                }
            }
        }
    }
