/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val viewStoriesAnonymouslyPatch =
    bytecodePatch(
        name = "View stories anonymously",
        default = false,
    ) {
        dependsOn(
            settingsPatch,
            interceptUriPatch,
            chatActionBarButtonPatch,
            inboxActionBarButtonPatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val pendingSeenClass = StorySeenUriBuilderFingerprint.originalClassDef.type
            val requestBuilder = StorySeenUriBuilderFingerprint.originalMethod
            val requestType = requestBuilder.returnType

            StorySeenUriBuilderFingerprint.let {
                val payloadRegister = it.method.getInstruction<FiveRegisterInstruction>(
                    it.instructionMatches[2].index,
                ).registerE
                val returnIndex = it.instructionMatches.last().index
                val requestRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA

                it.method.replaceInstruction(
                    returnIndex,
                    "invoke-static {v$requestRegister, p1, v$payloadRegister}, " +
                        "$STORY_SEEN_BRIDGE_DESCRIPTOR->observeRequest(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                )
                it.method.addInstruction(returnIndex + 1, "return-object v$requestRegister")
            }
            addStorySeenResultHooks(requestType, RequestSucceededExtensionFingerprint.method)

            PendingStoriesEmptyFingerprint.let {
                val returnIndex = it.instructionMatches.last().index
                val seenRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstructions(
                    returnIndex,
                    """
                    invoke-static {v$seenRegister}, $LINKS_DESCRIPTOR->setStorySeen(Z)Z
                    move-result v$seenRegister
                    """.trimIndent(),
                )
            }

            val consumed = ConsumedStoryFingerprint.instructionMatches
            val reelParentClass = ConsumedStoryFingerprint.originalMethod.parameterTypes[1].toString()
            val excludedItemPredicate = consumed[0].methodReference
            val reelModelField = consumed[6].fieldReference
            val reelModelClass = reelModelField.type
            val itemSeenTimestampMethod = consumed[7].methodReference
            val markLocalSeenMethod = consumed[9].methodReference
            val regularStoryPredicate = consumed[10].methodReference
            val excludedReelPredicate = consumed[13].methodReference
            val reelOwnerIdField = consumed[16].fieldReference
            val mediaField = consumed[17].fieldReference
            val mediaConversion = consumed[19].methodReference
            val aggregate = consumed[24].methodReference

            val aggregateMethod = addStoryAggregateBridge(
                mutableClassDefBy(aggregate.definingClass),
                aggregate,
                mediaConversion.returnType,
                consumed[21].methodReference,
                consumed[22].instruction.getReference<TypeReference>()!!.type,
                consumed[23].methodReference,
            )

            val reelTypeClass = ReelTypeFingerprint.originalClassDef.type
            val reelTypeField = classDefBy(reelModelClass).fields.single { it.type == reelTypeClass }

            val headerBindingMethod = StoryHeaderBindingFingerprint.matchOrNull()?.method
                ?: StoryToolbarBindingFingerprint.matchSingle().method
            val holderClass = headerBindingMethod.parameterTypes[STORY_HEADER_HOLDER_PARAMETER].toString()
            addStoryHeaderCaptureHook(headerBindingMethod)
            addStoryHeaderBridgeHook(
                StoryHeaderBindingExtensionFingerprint.method,
                holderClass,
                storyRootViewField(holderClass),
                reelModelField,
                reelTypeField,
            )

            val standardProgressMethod = StandardStoryProgressFingerprint.matchOrNull()?.method
                ?: DelegatedStoryProgressFingerprint.matchSingle().method
            listOf(
                Triple(
                    PromptStoryProgressFingerprint.method,
                    "captureFromPromptProgress",
                    PromptStoryProgressExtensionFingerprint,
                ),
                Triple(
                    standardProgressMethod,
                    "captureFromStandardProgress",
                    StandardStoryProgressExtensionFingerprint,
                ),
                Triple(
                    CompactStoryProgressFingerprint.method,
                    "captureFromCompactProgress",
                    CompactStoryProgressExtensionFingerprint,
                ),
            ).forEach { (progressMethod, bridgeMethodName, extensionFingerprint) ->
                val progressFields = classDefBy(progressMethod.definingClass).fields
                addStoryProgressCaptureHook(progressMethod, bridgeMethodName)
                addStoryProgressBridgeHook(
                    extensionFingerprint.method,
                    progressMethod.definingClass,
                    progressFields.single { it.type == USER_SESSION_CLASS },
                    progressFields.single { it.type == REEL_ITEM_CLASS },
                    progressFields.single { it.type == reelParentClass },
                    reelModelField,
                    reelTypeField,
                    storyRootViewField(progressMethod.definingClass),
                )
            }

            IsSelfExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $USER_SESSION_CLASS
                check-cast p1, $reelParentClass
                invoke-virtual {p0}, $USER_SESSION_CLASS->getUserId()Ljava/lang/String;
                move-result-object p0
                iget-object p1, p1, $reelModelField
                iget-object p1, p1, $reelOwnerIdField
                invoke-static {p0, p1}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result p0
                return p0
                """.trimIndent(),
            )
            IsSupportedExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $REEL_ITEM_CLASS
                check-cast p1, $reelParentClass
                invoke-virtual {p0}, $excludedItemPredicate
                move-result p2
                if-nez p2, :piko_unsupported_story
                invoke-virtual {p0}, $regularStoryPredicate
                move-result p2
                if-eqz p2, :piko_unsupported_story
                iget-object p1, p1, $reelModelField
                invoke-virtual {p1}, $excludedReelPredicate
                move-result p2
                if-nez p2, :piko_unsupported_story
                const/4 p2, 0x1
                return p2
                :piko_unsupported_story
                const/4 p2, 0x0
                return p2
                """.trimIndent(),
            )
            StoryIdExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $REEL_ITEM_CLASS
                invoke-virtual {p0}, $REEL_ITEM_CLASS->getId()Ljava/lang/String;
                move-result-object p0
                return-object p0
                """.trimIndent(),
            )
            MarkLocalExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $USER_SESSION_CLASS
                check-cast p1, $REEL_ITEM_CLASS
                check-cast p2, $reelParentClass
                iget-object p2, p2, $reelModelField
                invoke-virtual {p1}, $itemSeenTimestampMethod
                move-result-wide p3
                invoke-virtual {p2, p0, p3, p4}, $markLocalSeenMethod
                return-void
                """.trimIndent(),
            )
            BuildRequestExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $USER_SESSION_CLASS
                check-cast p1, $REEL_ITEM_CLASS
                check-cast p2, $reelParentClass
                invoke-virtual {p1}, $excludedItemPredicate
                move-result p3
                if-nez p3, :piko_request_unavailable
                invoke-virtual {p1}, $regularStoryPredicate
                move-result p3
                if-eqz p3, :piko_request_unavailable
                iget-object p3, p1, $mediaField
                if-eqz p3, :piko_request_unavailable
                iget-object p2, p2, $reelModelField
                invoke-virtual {p2}, $excludedReelPredicate
                move-result p1
                if-nez p1, :piko_request_unavailable
                iget-object p2, p2, $reelOwnerIdField
                const-string p1, "itas-android"
                const p4, -0x18b1a0fe
                invoke-virtual {p3, p1, p4}, $mediaConversion
                move-result-object p3
                if-eqz p3, :piko_request_unavailable
                new-instance p1, $pendingSeenClass
                invoke-direct {p1}, $pendingSeenClass-><init>()V
                invoke-static {p3, p0, p1, p2}, $aggregateMethod
                invoke-virtual {p1, p0}, $requestBuilder
                move-result-object p3
                return-object p3
                :piko_request_unavailable
                const/4 p3, 0x0
                return-object p3
                """.trimIndent(),
            )

            storySeenScheduleFingerprint(pendingSeenClass, requestBuilder).instructionMatches.let {
                val managerGetter = it.first().methodReference
                val scheduleRequest = it.last().methodReference
                ScheduleRequestExtensionFingerprint.method.addInstructions(
                    0,
                    """
                    check-cast p0, $USER_SESSION_CLASS
                    check-cast p1, $requestType
                    invoke-static {p0}, $managerGetter
                    move-result-object p0
                    invoke-virtual {p0, p1}, $scheduleRequest
                    return-void
                    """.trimIndent(),
                )
            }

            val requestInterface = ExecutorSchedulerFingerprint.originalMethod.parameterTypes.first().toString()
            val wrapperType = ExecutorSchedulerFingerprint.instructionMatches.last()
                .instruction.getReference<TypeReference>()!!.type
            executorWrapperRunFingerprint(wrapperType, requestInterface).let {
                val delegatedRunIndex = it.instructionMatches[1].index
                val requestRegister = it.instructionMatches[1].getInstruction<FiveRegisterInstruction>().registerC
                val handlerIndex = it.instructionMatches.last().index

                it.method.addInstruction(
                    handlerIndex + 1,
                    "invoke-static {}, $STORY_SEEN_SCOPE_DESCRIPTOR->exitActive()V",
                )
                it.method.replaceInstruction(
                    delegatedRunIndex,
                    "invoke-static {v$requestRegister}, $STORY_SEEN_SCOPE_DESCRIPTOR->enter(Ljava/lang/Object;)V",
                )
                it.method.addInstruction(
                    delegatedRunIndex + 1,
                    "invoke-interface {v$requestRegister}, $requestInterface->run()V",
                )
                it.method.addInstruction(
                    delegatedRunIndex + 2,
                    "invoke-static {v$requestRegister}, $STORY_SEEN_SCOPE_DESCRIPTOR->complete(Ljava/lang/Object;)V",
                )
            }

            enableSettings("viewStoriesAnonymously")
        }
    }
