/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "Download media",
        description = "Adds the ability to download posts, reels, stories, highlights, and saved collections",
    ) {
        dependsOn(
            settingsPatch,
            instagramDialogBoxEntity,
            mediaDataEntity,
            videoDataEntity,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            decoderEntity,
            handleStoryButtonPatch,
            hookFlagsPatch,
            saveAllMessagesPatch,
            hookOverflowMenuButton,
            debugOverflowMenuButtonPatch,
            hookReelOverflowMenuButton,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            SavedCollectionOptionsActionSheetFingerprint.method.apply {
                val markerIndex = SavedCollectionOptionsActionSheetFingerprint.stringMatches.single().index
                val constructorIndex = indexOfFirstInstructionOrThrow(markerIndex, Opcode.INVOKE_DIRECT)
                val constructorInstruction = getInstruction(constructorIndex)
                val constructorReference =
                    constructorInstruction.getReference<MethodReference>()
                        ?: throw PatchException("Could not resolve the saved collection action sheet builder")
                val builderType = constructorReference.definingClass
                val builderRegister = constructorInstruction.registersUsed.firstOrNull()
                    ?: throw PatchException("Could not resolve the saved collection action sheet builder register")

                val helperReferences =
                    instructions
                        .drop(constructorIndex + 1)
                        .mapNotNull { instruction ->
                            if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                                null
                            } else {
                                instruction.getReference<MethodReference>()
                            }
                        }.filter { reference ->
                            reference.parameterTypes.firstOrNull()?.toString() == builderType &&
                                reference.returnType == "V"
                        }.distinctBy { reference ->
                            "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString()})"
                        }

                val helperReference =
                    helperReferences.singleOrNull()
                        ?: throw PatchException(
                            "Expected one normal saved collection action helper, found ${helperReferences.size}",
                        )
                val helperMethod =
                    mutableClassDefBy(helperReference.definingClass)
                        .methods
                        .singleOrNull { candidate ->
                            candidate.name == helperReference.name &&
                                candidate.parameterTypes == helperReference.parameterTypes &&
                                candidate.returnType == helperReference.returnType
                        } ?: throw PatchException("Could not resolve the normal saved collection action helper")
                val normalActionReferences =
                    helperMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.INVOKE_VIRTUAL && instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE) {
                            null
                        } else {
                            instruction.getReference<MethodReference>()
                        }
                    }.filter { reference ->
                        reference.definingClass == builderType &&
                            reference.parameterTypes.map(CharSequence::toString) ==
                            listOf("Landroid/view/View\$OnClickListener;", "I") &&
                            reference.returnType == "V"
                    }.distinctBy(MethodReference::getName)
                val normalActionReference =
                    normalActionReferences.singleOrNull()
                        ?: throw PatchException(
                            "Expected one normal saved collection action method, found ${normalActionReferences.size}",
                        )
                val builderClass = mutableClassDefBy(builderType)
                val normalActionMethod =
                    builderClass.methods.singleOrNull { candidate ->
                        candidate.name == normalActionReference.name &&
                            candidate.parameterTypes == normalActionReference.parameterTypes &&
                            candidate.returnType == normalActionReference.returnType
                    } ?: throw PatchException("Could not resolve the normal saved collection action method")
                val normalColorReferences =
                    normalActionMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                            null
                        } else {
                            instruction.getReference<MethodReference>()
                        }
                    }.filter { reference -> reference.returnType == "I" }
                val normalColorReference =
                    normalColorReferences.singleOrNull()
                        ?: throw PatchException(
                            "Expected one normal saved collection action color method, found ${normalColorReferences.size}",
                        )
                val stringActionMethods =
                    builderClass.methods.filter { candidate ->
                        candidate.parameterTypes.map(CharSequence::toString) ==
                            listOf("Ljava/lang/String;", "Landroid/view/View\$OnClickListener;") &&
                            candidate.returnType == "V" &&
                            candidate.instructions.any { instruction ->
                                instruction.getReference<MethodReference>() == normalColorReference
                            }
                    }
                val stringActionMethod =
                    stringActionMethods.singleOrNull()
                        ?: throw PatchException(
                            "Expected one normal string action method, found ${stringActionMethods.size}",
                        )

                AddCollectionMenuItemExtensionFingerprint.changeFirstString(stringActionMethod.name)

                val showBuilderIndex =
                    instructions.indexOfLast { instruction -> instruction.opcode == Opcode.NEW_INSTANCE }
                if (showBuilderIndex <= constructorIndex) {
                    throw PatchException("Could not find where the saved collection action sheet is shown")
                }
                addInstruction(
                    showBuilderIndex,
                    "invoke-static {v$builderRegister, p0}, $DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;->addMenuItem(Ljava/lang/Object;Ljava/lang/Object;)V",
                )
            }

            SavedCollectionPageRequestFingerprint.apply {
                val fragmentType = classDef.type
                val requestMethod = method
                val requestCallers =
                    classDef.methods.filter { candidate ->
                        candidate.parameterTypes.isEmpty() &&
                            candidate.returnType == "V" &&
                            candidate.instructions.any { instruction ->
                                instruction.getReference<MethodReference>()?.matches(requestMethod) == true
                            }
                    }

                val nextPageCandidates =
                    requestCallers.mapNotNull { candidate ->
                        val sourceFields =
                            candidate.instructions.mapNotNull { instruction ->
                                instruction.getReference<FieldReference>()
                            }.filter { reference ->
                                reference.definingClass == fragmentType &&
                                    reference.type.startsWith("L")
                            }.distinctBy { reference -> "${reference.name}:${reference.type}" }
                        val booleanCalls =
                            candidate.instructions.mapNotNull { instruction ->
                                instruction.getReference<MethodReference>()
                            }.filter { reference ->
                                reference.parameterTypes.isEmpty() && reference.returnType == "Z"
                            }.distinctBy { reference ->
                                "${reference.definingClass}->${reference.name}"
                            }
                        val pairs =
                            sourceFields.flatMap { field ->
                                booleanCalls
                                    .filter { call -> call.definingClass == field.type }
                                    .map { call -> Triple(candidate, field, call) }
                            }
                        pairs.singleOrNull()
                    }
                val (nextPageMethod, sourceField, canLoadMoreMethodReference) =
                    nextPageCandidates.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection next-page method, " +
                                "found ${nextPageCandidates.size}",
                        )

                val sourceClass = mutableClassDefBy(sourceField.type)
                val canLoadMoreMethod =
                    sourceClass.methods.singleOrNull { candidate ->
                        candidate.name == canLoadMoreMethodReference.name &&
                            candidate.parameterTypes == canLoadMoreMethodReference.parameterTypes &&
                            candidate.returnType == canLoadMoreMethodReference.returnType
                    } ?: throw PatchException("Could not resolve the native collection paginator method")
                val hasCursorReferences =
                    canLoadMoreMethod.instructions.mapNotNull { instruction ->
                        instruction.getReference<MethodReference>()
                    }.filter { reference ->
                        reference.definingClass == sourceField.type &&
                            reference.name != canLoadMoreMethod.name &&
                            reference.parameterTypes.isEmpty() &&
                            reference.returnType == "Z"
                    }.distinctBy(MethodReference::getName)
                val hasCursorReference =
                    hasCursorReferences.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection cursor method, " +
                                "found ${hasCursorReferences.size}",
                        )

                val sourceStateFields =
                    canLoadMoreMethod.instructions.mapNotNull { instruction ->
                        instruction.getReference<FieldReference>()
                    }.filter { reference ->
                        reference.definingClass == sourceField.type &&
                            reference.type.startsWith("L")
                    }.distinctBy { reference -> "${reference.name}:${reference.type}" }
                val sourceStateField =
                    sourceStateFields.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection state field, " +
                                "found ${sourceStateFields.size}",
                        )
                val stateClass = mutableClassDefBy(sourceStateField.type)
                val hasMoreFields =
                    canLoadMoreMethod.instructions.mapNotNull { instruction ->
                        instruction.getReference<FieldReference>()
                    }.filter { reference ->
                        reference.definingClass == sourceStateField.type && reference.type == "Z"
                    }.distinctBy(FieldReference::getName)
                val hasMoreField =
                    hasMoreFields.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection has-more field, " +
                                "found ${hasMoreFields.size}",
                        )
                val requestAllowedMethods =
                    stateClass.methods.filter { candidate ->
                        candidate.parameterTypes.map(CharSequence::toString) == listOf("Z") &&
                            candidate.returnType == "Z"
                    }
                val requestAllowedMethod =
                    requestAllowedMethods.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection request-state method, " +
                                "found ${requestAllowedMethods.size}",
                        )

                val refreshCandidates =
                    requestCallers.filter { candidate ->
                        candidate !== nextPageMethod &&
                            candidate.requestFlags(requestMethod) == (1 to 1)
                    }
                val refreshMethod =
                    refreshCandidates.singleOrNull()
                        ?: throw PatchException(
                            "Expected one native saved collection refresh method, " +
                                "found ${refreshCandidates.size}; callers=" +
                                requestCallers.joinToString(" | ") { candidate ->
                                    candidate.requestDebug(requestMethod)
                                },
                        )

                ReadCollectionSourceExtensionFingerprint.changeFirstString(sourceField.name)
                ReadCollectionSourceStateExtensionFingerprint.changeFirstString(sourceStateField.name)
                CollectionSourceHasCursorExtensionFingerprint.changeFirstString(hasCursorReference.name)
                CollectionSourceCanLoadMoreExtensionFingerprint.changeFirstString(canLoadMoreMethod.name)
                CollectionStateHasMoreExtensionFingerprint.changeFirstString(hasMoreField.name)
                CollectionStateRequestAllowedExtensionFingerprint.changeFirstString(requestAllowedMethod.name)
                InvokeCollectionNextPageExtensionFingerprint.changeFirstString(nextPageMethod.name)
                InvokeCollectionRefreshExtensionFingerprint.changeFirstString(refreshMethod.name)
            }

            addOverflowMenuButtonAttributes("PIKO_DOWNLOAD", "downloadOverflowButton")

            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {

                val appActivityField = classDef.fields.first { it.type == "Landroid/app/Activity;" }

                classDef.methods
                    .first { it.returnType == "V" && it.name != "<init>" }
                    .apply {
                        addInstructionsWithLabels(
                            0,
                            """
                            iget-object v0, p1, $appActivityField
                            move-object v1, p2
                            invoke-static {v0, v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Landroid/content/Context;Ljava/lang/Object;)Z
                            move-result v1
                            if-nez v1, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }
            }

            enableSettings("downloadMedia")
            addFlags("simpleOverflowMenuFlags")
        }
    }

private fun MethodReference.matches(method: MutableMethod): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        parameterTypes.map(CharSequence::toString) ==
        method.parameterTypes.map(CharSequence::toString) &&
        returnType == method.returnType

private fun MutableMethod.requestFlags(requestMethod: MutableMethod): Pair<Int, Int>? {
    val requestCalls =
        instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.getReference<MethodReference>()?.matches(requestMethod) == true
            }
        }
    val callIndex = requestCalls.singleOrNull() ?: return null
    val registers = getInstruction(callIndex).registersUsed
    if (registers.size != 3) return null

    val firstFlag = findPreviousNarrowLiteral(callIndex, registers[1]) ?: return null
    val secondFlag = findPreviousNarrowLiteral(callIndex, registers[2]) ?: return null
    return firstFlag to secondFlag
}

private fun MutableMethod.findPreviousNarrowLiteral(
    instructionIndex: Int,
    register: Int,
): Int? {
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = getInstruction(index)
        if (
            instruction is OneRegisterInstruction &&
            instruction is NarrowLiteralInstruction &&
            instruction.registerA == register
        ) {
            return instruction.narrowLiteral
        }
    }
    return null
}

private fun MutableMethod.requestDebug(requestMethod: MutableMethod): String {
    val callIndex =
        instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>()?.matches(requestMethod) == true
        }
    if (callIndex < 0) return "$name(no request call)"

    val start = maxOf(0, callIndex - 16)
    val recent =
        instructions.subList(start, callIndex + 1).joinToString("; ") { instruction ->
            buildString {
                append(instruction.opcode.name)
                (instruction as? OneRegisterInstruction)?.let { append(" v${it.registerA}") }
                (instruction as? NarrowLiteralInstruction)?.let { append(" #${it.narrowLiteral}") }
            }
        }
    return "$name flags=${requestFlags(requestMethod)} " +
        "registers=${getInstruction(callIndex).registersUsed} recent=[$recent]"
}
