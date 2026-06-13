/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode

val tweetInfoEntityPatch =
    bytecodePatch(
        description = "For tweet info entity reflection",
    ) {
        execute {
            GetItemActionDelegateFingerprint.method.apply {
                val invokeStaticMatch = instructions.firstOrNull {
                    it.opcode == Opcode.INVOKE_STATIC && it.methodExtractor().name == "parse"
                } ?: throw PatchException("Failed to find parse INVOKE_STATIC in GetItemActionDelegateFingerprint")

                val itemActionDelegateClass = invokeStaticMatch.methodExtractor().returnType
                GetItemActionDelegateExtensionFingerprint.changeFirstString(itemActionDelegateClass)

                val igetObject = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in GetItemActionDelegateFingerprint")
                GetTweetInfoExtensionFingerprint.changeFirstString(igetObject.fieldExtractor().name)
            }

            GetTweetInfoActionDelegateFingerprint.method.apply {
                val igetObject = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in GetTweetInfoActionDelegateFingerprint")
                GetTweetInfoActionDelegateExtensionFingerprint.changeFirstString(igetObject.fieldExtractor().name)
            }

            GetTimelineEntryIdFingerprint.method.apply {
                val igetObject = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in GetTimelineEntryIdFingerprint")
                GetTimelineEntryIdExtensionFingerprint.changeFirstString(igetObject.fieldExtractor().name)
            }

            GetStatusIdFingerprint.method.apply {
                val igetWide = instructions.firstOrNull { it.opcode == Opcode.IGET_WIDE }
                    ?: throw PatchException("Failed to find IGET_WIDE in GetStatusIdFingerprint")
                GetStatusIdExtensionFingerprint.changeFirstString(igetWide.fieldExtractor().name)
            }

            GetStatusInfoFingerprint.method.apply {
                val igetObject = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in GetStatusInfoFingerprint")
                val statusInfoField = igetObject.fieldExtractor()
                GetStatusInfoExtensionFingerprint.changeFirstString(statusInfoField.name)

                val statusInfoMethods = mutableClassDefBy(statusInfoField.type).methods
                val getLongMethod = statusInfoMethods.firstOrNull { it.returnType == "J" }
                    ?: throw PatchException("Failed to find Long method in ${statusInfoField.type}")
                GetStatusInfoExtensionFingerprint.changeStringAt(1, getLongMethod.name)
            }
        }
    }
