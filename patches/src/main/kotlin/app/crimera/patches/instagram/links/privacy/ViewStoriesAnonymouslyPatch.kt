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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val viewStoriesAnonymouslyPatch =
    bytecodePatch(
        name = "View stories anonymously",
    ) {
        dependsOn(
            settingsPatch,
            interceptUriPatch,
            chatActionBarButtonPatch,
            inboxActionBarButtonPatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val pendingSeenClass = StorySeenUriBuilderFingerprint.classDef.type
            val requestBuilder = StorySeenUriBuilderFingerprint.method
            addNormalStorySeenObservationHook(requestBuilder)
            addStorySeenResultHooks(requestBuilder, RequestSucceededExtensionFingerprint.method) {
                mutableClassDefBy(it)
            }

            val emptyStateMethods =
                StorySeenUriBuilderFingerprint.classDef.methods.filter { candidate ->
                    if (candidate.returnType != "Z" || candidate.parameterTypes.isNotEmpty()) {
                        return@filter false
                    }
                    val emptyCalls =
                        candidate.implementation?.instructions?.mapNotNull { instruction ->
                            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        }?.filter {
                            it.name == "isEmpty" && it.parameterTypes.isEmpty() && it.returnType == "Z"
                        }.orEmpty()
                    emptyCalls.size == 3
                }
            val emptyStateMethod =
                emptyStateMethods.singleOrNull()
                    ?: throw PatchException(
                        "Expected one structural pending-story empty check, found ${emptyStateMethods.size}",
                    )
            emptyStateMethod.apply {
                val lastIfEqzIndex = instructions.last { it.opcode == Opcode.IF_EQZ }.location.index
                val returnIndex = indexOfFirstInstruction(lastIfEqzIndex, Opcode.RETURN)
                val seenRegister = getInstruction(returnIndex).registersUsed.single()
                addInstructions(
                    returnIndex,
                    """
                    invoke-static {v$seenRegister}, $LINKS_DESCRIPTOR->setStorySeen(Z)Z
                    move-result v$seenRegister
                    """.trimIndent(),
                )
            }

            val consumedMethod = ConsumedStoryFingerprint.method
            val reelParentClass = consumedMethod.parameterTypes[1].toString()
            val consumedInstructions = consumedMethod.instructions
            val mediaConversionEntry =
                consumedInstructions.mapIndexedNotNull { index, instruction ->
                    val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
                    if (
                        instruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                        instruction.registersUsed.size == 3 &&
                        reference.definingClass == MEDIA_CLASS &&
                        reference.parameterTypes == listOf(STRING_CLASS, "I") &&
                        reference.returnType.startsWith("L")
                    ) IndexedValue(index, reference) else null
                }.singleOrNull()
                    ?: throw PatchException("Expected one story media conversion call")
            val mediaConversionIndex = mediaConversionEntry.index
            val mediaConversion = mediaConversionEntry.value

            val mediaField = deriveStoryMediaField(consumedMethod, mediaConversionIndex)
            val localSeenReferences =
                deriveLocalStorySeenReferences(
                    consumedMethod,
                    reelParentClass,
                    mediaConversionIndex,
                )
            val reelModelField = localSeenReferences.reelModelField
            val reelModelClass = reelModelField.type
            val itemSeenTimestampMethod = localSeenReferences.itemSeenTimestampMethod
            val markLocalSeenMethod = localSeenReferences.markLocalSeenMethod
            val predicates =
                deriveStoryPredicateReferences(consumedMethod, reelModelClass, mediaConversionIndex)
            val excludedItemPredicate = predicates.excludedItem
            val regularStoryPredicate = predicates.regularStory
            val excludedReelPredicate = predicates.excludedReel

            val aggregateEntry =
                deriveAggregateInvocation(
                    consumedMethod,
                    mediaConversionIndex + 1,
                    pendingSeenClass,
                    mediaConversion.returnType,
                )
            val reelOwnerIdField =
                deriveReelOwnerIdField(
                    consumedMethod,
                    aggregateEntry.index,
                    reelModelClass,
                    aggregateEntry.ownerRegister,
                )
            val reelTypeClass = ReelTypeFingerprint.classDef.type
            val reelTypeField =
                mutableClassDefBy(reelModelClass).fields.filter { it.type == reelTypeClass }.singleOrNull()
                    ?: throw PatchException("Could not derive the reel-type field")

            val headerBindingMethod = StoryHeaderBindingFingerprint.method
            if (!headerBindingMethod.isStoryHeaderBindingMethod(reelParentClass)) {
                throw PatchException(
                    "Story-header trace matched a method without the expected typed parameters",
                )
            }
            fun uniqueHeaderParameterIndex(type: String, description: String): Int {
                val matches =
                    headerBindingMethod.parameterTypes.mapIndexedNotNull { index, parameterType ->
                        if (parameterType.toString() == type) index else null
                    }
                return matches.singleOrNull()
                    ?: throw PatchException(
                        "Expected one story-header $description parameter, found ${matches.size}",
                    )
            }
            val sessionParameterIndex =
                uniqueHeaderParameterIndex(USER_SESSION_CLASS, "session")
            val itemParameterIndex =
                uniqueHeaderParameterIndex(REEL_ITEM_CLASS, "item")
            val reelParameterIndex =
                uniqueHeaderParameterIndex(reelParentClass, "reel")
            val holderCandidates =
                headerBindingMethod.parameterTypes.mapIndexedNotNull { index, parameterType ->
                    val descriptor = parameterType.toString()
                    if (!descriptor.startsWith("L") || descriptor in setOf(
                            USER_SESSION_CLASS,
                            REEL_ITEM_CLASS,
                            reelParentClass,
                        )
                    ) {
                        return@mapIndexedNotNull null
                    }
                    val fieldTypes = mutableClassDefBy(descriptor).fields.map { it.type }.toSet()
                    if (
                        fieldTypes.containsAll(
                            setOf(USER_SESSION_CLASS, REEL_ITEM_CLASS, reelParentClass, VIEW_CLASS),
                        )
                    ) IndexedValue(index, descriptor) else null
                }
            val holderEntry =
                holderCandidates.singleOrNull()
                    ?: throw PatchException(
                        "Expected one structurally typed story-header holder, found ${holderCandidates.size}",
                    )
            val holderClass = mutableClassDefBy(holderEntry.value)
            val rootViewField =
                deriveConstructorBoundRootViewField(holderEntry.value, holderClass.methods)
                    ?: throw PatchException(
                        "Could not derive the constructor-bound story root view",
                    )
            addStoryHeaderCaptureHook(
                headerBindingMethod,
                sessionParameterIndex,
                itemParameterIndex,
                reelParameterIndex,
                holderEntry.index,
            )
            addStoryHeaderBridgeHook(
                StoryHeaderBindingExtensionFingerprint.method,
                holderEntry.value,
                rootViewField,
                reelModelField,
                reelTypeField,
            )

            listOf(
                Triple(
                    PromptStoryProgressFingerprint.method,
                    "captureFromPromptProgress",
                    PromptStoryProgressExtensionFingerprint.method,
                ),
                Triple(
                    StandardStoryProgressFingerprint.method,
                    "captureFromStandardProgress",
                    StandardStoryProgressExtensionFingerprint.method,
                ),
                Triple(
                    CompactStoryProgressFingerprint.method,
                    "captureFromCompactProgress",
                    CompactStoryProgressExtensionFingerprint.method,
                ),
            ).forEach { (progressMethod, bridgeMethodName, extensionMethod) ->
                val progressClass = mutableClassDefBy(progressMethod.definingClass)
                val sessionField =
                    progressClass.fields.filter { it.type == USER_SESSION_CLASS }.singleOrNull()
                        ?: throw PatchException(
                            "Expected one structurally typed story-progress session field",
                        )
                val itemField =
                    progressClass.fields.filter { it.type == REEL_ITEM_CLASS }.singleOrNull()
                        ?: throw PatchException(
                            "Expected one structurally typed story-progress item field",
                        )
                val reelField =
                    progressClass.fields.filter { it.type == reelParentClass }.singleOrNull()
                        ?: throw PatchException(
                            "Expected one structurally typed story-progress reel field",
                        )
                val progressHierarchy = buildList {
                    val visitedClasses = mutableSetOf<String>()
                    var classDescriptor: String? = progressMethod.definingClass
                    while (
                        classDescriptor != null &&
                            classDescriptor != "Ljava/lang/Object;" &&
                            visitedClasses.add(classDescriptor)
                    ) {
                        val hierarchyClass = mutableClassDefBy(classDescriptor)
                        add(classDescriptor to hierarchyClass.methods)
                        classDescriptor = hierarchyClass.superclass
                    }
                }
                val rootViewField =
                    deriveHierarchyConstructorBoundRootViewField(
                        progressHierarchy,
                    ) ?: throw PatchException(
                        "Could not derive the constructor-bound story-progress root view",
                    )
                addStoryProgressCaptureHook(progressMethod, bridgeMethodName)
                addStoryProgressBridgeHook(
                    extensionMethod,
                    progressMethod.definingClass,
                    sessionField,
                    itemField,
                    reelField,
                    reelModelField,
                    reelTypeField,
                    rootViewField,
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
            val storyIdMethod =
                mutableClassDefBy(REEL_ITEM_CLASS).methods.filter {
                    it.name == "getId" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == STRING_CLASS
                }.singleOrNull()
                    ?: throw PatchException("Could not identify the stable ReelItem story id accessor")
            StoryIdExtensionFingerprint.method.addInstructions(
                0,
                """
                check-cast p0, $REEL_ITEM_CLASS
                invoke-virtual {p0}, $storyIdMethod
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
                invoke-static {p3, p0, p1, p2}, ${aggregateEntry.reference}
                invoke-virtual {p1, p0}, $requestBuilder
                move-result-object p3
                return-object p3
                :piko_request_unavailable
                const/4 p3, 0x0
                return-object p3
                """.trimIndent(),
            )

            val storeScheduleFingerprint =
                object : Fingerprint(
                    parameters = listOf(pendingSeenClass),
                    returnType = "V",
                    custom = { methodDef, _ ->
                        methodDef.implementation?.instructions?.any { instruction ->
                            instruction.getReference<MethodReference>()?.matches(requestBuilder) == true
                        } == true
                    },
                ) {}
            val storeScheduleMethod = storeScheduleFingerprint.method
            val requestType = requestBuilder.returnType
            val scheduleReferences =
                deriveRequestScheduleReferences(
                    storeScheduleMethod,
                    pendingSeenClass,
                    requestBuilder,
                )
            val managerGetter = scheduleReferences.managerGetter
            val scheduleRequest = scheduleReferences.scheduleRequest
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

            val schedulerMethod = ExecutorSchedulerFingerprint.method
            val requestInterface = schedulerMethod.parameterTypes.first().toString()
            val wrapperConstructor =
                uniqueMethodReference(schedulerMethod, "executor request-wrapper constructor") {
                    it.name == "<init>" && it.parameterTypes.firstOrNull() == requestInterface
                }
            val wrapperClass = mutableClassDefBy(wrapperConstructor.definingClass)
            val wrappedRequestField =
                wrapperClass.fields.filter { it.type == requestInterface }.singleOrNull()
                    ?: throw PatchException("Could not derive the wrapped request field")
            val wrapperRunMethod =
                wrapperClass.methods.filter {
                    it.name == "run" && it.parameterTypes.isEmpty() && it.returnType == "V"
                }.singleOrNull()
                    ?: throw PatchException("Could not derive the executor wrapper run method")
            val executorRunHook =
                deriveExecutorRunHook(wrapperRunMethod, wrappedRequestField, requestInterface)
            val delegatedRunInstruction =
                wrapperRunMethod.getInstruction(executorRunHook.delegatedRunIndex)
            executorRunHook.exceptionHandlerIndexes.sortedDescending().forEach { handlerIndex ->
                wrapperRunMethod.addInstruction(
                    handlerIndex + 1,
                    "invoke-static {}, $STORY_SEEN_SCOPE_DESCRIPTOR->exitActive()V",
                )
            }
            val delegatedRunIndex = delegatedRunInstruction.location.index
            wrapperRunMethod.replaceInstruction(
                delegatedRunIndex,
                "invoke-static {v${executorRunHook.requestRegister}}, $STORY_SEEN_SCOPE_DESCRIPTOR->enter(Ljava/lang/Object;)V",
            )
            wrapperRunMethod.addInstruction(
                delegatedRunIndex + 1,
                "invoke-interface {v${executorRunHook.requestRegister}}, $requestInterface->run()V",
            )
            wrapperRunMethod.addInstruction(
                delegatedRunIndex + 2,
                "invoke-static {v${executorRunHook.requestRegister}}, $STORY_SEEN_SCOPE_DESCRIPTOR->complete(Ljava/lang/Object;)V",
            )

            enableSettings("viewStoriesAnonymously")
        }
    }
