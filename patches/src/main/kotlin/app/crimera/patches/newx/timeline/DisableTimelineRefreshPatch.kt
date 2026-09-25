package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val URT_REPOSITORY_PACKAGE = "Lcom/x/repositories/urt/"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineScrollPositionStore;"
private const val TIMELINE_REFRESH_GATE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineRefreshGate;"
private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val GET_INTENT_DESCRIPTOR = "Landroid/app/Activity;->getIntent()$INTENT_DESCRIPTOR"
private const val MARK_POST_DEEP_LINK_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->markPostDeepLink($INTENT_DESCRIPTOR)V"
private const val CONSUME_POST_DEEP_LINK_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->consumePostDeepLink()Z"
private const val CONSUME_FOR_YOU_FILTER_REFRESH_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->consumeForYouFilterRefresh()Z"
private const val IS_POST_DEEP_LINK_PENDING_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->isPostDeepLinkPending()Z"
private const val IS_FOR_YOU_FILTER_REFRESH_PENDING_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->isForYouFilterRefreshPending()Z"
private const val IS_TIMELINE_DATA_EMPTY_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->isTimelineDataEmpty($LIST_DESCRIPTOR)Z"
private const val RESTORE_TIMELINE_POSITION_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->restore($ENUM_DESCRIPTOR)[I"
private const val URT_SUPPRESS_LABEL = "piko_newx_refresh_urt_suppress"
private const val URT_CHECK_POSITION_LABEL = "piko_newx_refresh_urt_check_position"

private object NewXMainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)

private object NewXMainActivityOnNewIntentFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    name = "onNewIntent",
    parameters = listOf("Landroid/content/Intent;"),
    returnType = "V",
)

private object NewXHomeReselectFingerprint : Fingerprint(
    definingClass = "Lcom/x/home/tabbed/",
    parameters = listOf("Z", "Z"),
    returnType = "Z",
    filters =
        listOf(
            string("timeline_auto_refresh_on_foreground_timeout_millis"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "getInt",
                parameters = listOf("Ljava/lang/String;", "I"),
                returnType = "I",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        ),
)

private fun newXUrtRepositoryRequestFingerprint(requestCall: MethodReference) =
    Fingerprint(
        definingClass = URT_REPOSITORY_PACKAGE,
        name = requestCall.name,
        parameters = requestCall.parameterTypes.map(CharSequence::toString),
        returnType = requestCall.returnType.toString(),
        custom = { method, _ -> method.implementation != null },
    )

private object NewXUrtAutoRefreshEventFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    parameters = listOf("L"),
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "AUTO_REFRESH",
                type = "L",
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = URT_REPOSITORY_PACKAGE,
                parameters = listOf("L", "L"),
                returnType = "V",
            ),
        ),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions?.toList().orEmpty()
        val autoRefreshFieldReads = instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.SGET_OBJECT) return@mapIndexedNotNull null
            val reference =
                instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()
                    ?: return@mapIndexedNotNull null
            index.takeIf {
                reference.name == "AUTO_REFRESH" &&
                    reference.type.toString().startsWith("L")
            }
        }
        val refreshRequestCalls = instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@mapIndexedNotNull null
            val reference = instruction.getReference<MethodReference>()
                ?: return@mapIndexedNotNull null
            index.takeIf {
                reference.definingClass.startsWith(URT_REPOSITORY_PACKAGE) &&
                    reference.parameterTypes.size == 2 &&
                    reference.parameterTypes.all { it.toString().startsWith("L") } &&
                    reference.returnType.toString() == "V"
            }
        }
        autoRefreshFieldReads.size == 1 && refreshRequestCalls.size == 1
    },
)

@Suppress("unused")
val disableTimelineRefreshPatch =
    bytecodePatch(
        name = "NewX: Disable automatic timeline refresh",
        description = "Prevents automatic timeline jumps on startup and foregrounding.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val disableTimelineRefresh =
            newXToggle(
                id = "newx.timeline.disable_refresh",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_disable_timeline_refresh"),
                order = 100,
                defaultValue = true,
            )

        execute {
            val mainActivityOnCreateMatches = NewXMainActivityOnCreateFingerprint.scopedMatchAll()
            if (mainActivityOnCreateMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX MainActivity onCreate method, found " +
                        "${mainActivityOnCreateMatches.size}: " +
                        mainActivityOnCreateMatches.joinToString { it.originalMethod.toString() },
                )
            }
            mainActivityOnCreateMatches.single().method.apply {
                val method = this
                // The mark arms the gate for the intent this activity was created with. It is entry
                // state, so a path that re-enters the first instruction has to keep skipping it.
                method.insertHook(
                    index = 0,
                    relocateBranchTargets = false,
                ) {
                    val intentRegister = scratchRegister()
                    invokeVirtual(methodReference(GET_INTENT_DESCRIPTOR), method.p0Register)
                    moveResult(intentRegister, INTENT_DESCRIPTOR)
                    invokeStatic(methodReference(MARK_POST_DEEP_LINK_DESCRIPTOR), intentRegister)
                }
            }

            val mainActivityOnNewIntentMatches = NewXMainActivityOnNewIntentFingerprint.scopedMatchAll()
            if (mainActivityOnNewIntentMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX MainActivity onNewIntent method, found " +
                        "${mainActivityOnNewIntentMatches.size}: " +
                        mainActivityOnNewIntentMatches.joinToString { it.originalMethod.toString() },
                )
            }
            mainActivityOnNewIntentMatches.single().method.apply {
                val method = this
                // Same entry mark as onCreate: the delivered intent (p1) is what gets armed.
                method.insertHook(
                    index = 0,
                    relocateBranchTargets = false,
                ) {
                    invokeStatic(methodReference(MARK_POST_DEEP_LINK_DESCRIPTOR), method.p0Register + 1)
                }
            }

            val homeMatches = NewXHomeReselectFingerprint.scopedMatchAll()
            if (homeMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX home reselect handler, found ${homeMatches.size}: " +
                        homeMatches.joinToString { it.originalMethod.toString() },
                )
            }
            homeMatches.single().method.apply {
                val method = this
                val read =
                    disableTimelineRefresh.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                // Every path into the handler has to pass the toggle check: a branch that used to
                // land on the first instruction would otherwise bypass the guard.
                method.insertHook(
                    index = read.nextIndex,
                    relocateBranchTargets = true,
                ) {
                    ifEqz(read.register, Target.Original)
                    constInt(read.register, 0)
                    returnValue(read.register)
                }
            }

            val autoRefreshEventMatches = NewXUrtAutoRefreshEventFingerprint.scopedMatchAll()
            if (autoRefreshEventMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT automatic-refresh event handler, found " +
                        "${autoRefreshEventMatches.size}: " +
                        autoRefreshEventMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val autoRefreshEventMethod = autoRefreshEventMatches.single().method
            val autoRefreshFieldCandidates =
                autoRefreshEventMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.SGET_OBJECT) return@filter false
                    val reference = indexedInstruction.value.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()
                        ?: return@filter false
                    reference.name == "AUTO_REFRESH" &&
                        reference.type.toString().startsWith("L")
                }
            if (autoRefreshFieldCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX URT automatic-refresh request-type read, found " +
                        "${autoRefreshFieldCandidates.size}",
                )
            }

            val autoRefreshFieldCandidate = autoRefreshFieldCandidates.single()
            val autoRefreshFieldInstruction =
                autoRefreshFieldCandidate.value as? OneRegisterInstruction
                    ?: throw PatchException("NewX URT automatic-refresh request-type read has no register layout")
            val autoRefreshFieldReference =
                autoRefreshFieldCandidate.value.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()
                    ?: throw PatchException("NewX URT automatic-refresh request-type read has no field reference")
            val autoRefreshTypeDescriptor = autoRefreshFieldReference.type.toString()
            val autoRefreshRegister = autoRefreshFieldInstruction.registerA
            val requestCallCandidate =
                autoRefreshEventMethod.instructions.withIndex().firstOrNull { indexedInstruction ->
                    if (indexedInstruction.index <= autoRefreshFieldCandidate.index ||
                        indexedInstruction.value.opcode != Opcode.INVOKE_INTERFACE
                    ) {
                        return@firstOrNull false
                    }
                    val reference = indexedInstruction.value.getReference<MethodReference>()
                        ?: return@firstOrNull false
                    reference.definingClass.startsWith(URT_REPOSITORY_PACKAGE) &&
                        reference.parameterTypes.firstOrNull()?.toString() == autoRefreshTypeDescriptor &&
                        reference.parameterTypes.getOrNull(1)?.toString()?.startsWith("L") == true &&
                        reference.parameterTypes.size == 2 &&
                        reference.returnType.toString() == "V"
                } ?: throw PatchException("NewX URT automatic-refresh request call was not found")
            val requestCallReference =
                requestCallCandidate.value.getReference<MethodReference>()
                    ?: throw PatchException("NewX URT automatic-refresh request call has no method reference")
            val requestCallInstruction =
                requestCallCandidate.value as? FiveRegisterInstruction
                    ?: throw PatchException("NewX URT automatic-refresh request call has an unsupported register layout")
            if (requestCallInstruction.registerCount != 3) {
                throw PatchException(
                    "Unexpected NewX URT automatic-refresh request register count: " +
                        requestCallInstruction.registerCount,
                )
            }
            val repositoryReceiverRegister = requestCallInstruction.registerC
            if (repositoryReceiverRegister !in 0..15) {
                throw PatchException(
                    "NewX URT automatic-refresh repository register is not encodable: " +
                        "v$repositoryReceiverRegister",
                )
            }

            val urtRepoMatches =
                newXUrtRepositoryRequestFingerprint(requestCallReference).scopedMatchAll()
            if (urtRepoMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT repository request handler, found ${urtRepoMatches.size}: " +
                        urtRepoMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val urtRepoMatch = urtRepoMatches.single()
            val repoDescriptor = urtRepoMatch.originalMethod.definingClass
            val requestTypeDescriptor =
                requestCallReference.parameterTypes.firstOrNull()?.toString()?.takeIf { it.startsWith("L") }
                    ?: throw PatchException("NewX URT repository request has no object request-type parameter")
            val repositoryClass = mutableClassDefBy(repoDescriptor)
            val timelineGetterMatches =
                repositoryClass.methods.filter { method ->
                    val returnType = method.returnType.toString()
                    method.parameterTypes.isEmpty() &&
                        returnType.startsWith("L") &&
                        runCatching { mutableClassDefBy(returnType).superclass == ENUM_DESCRIPTOR }.getOrDefault(false)
                }
            if (timelineGetterMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT timeline getter on $repoDescriptor, " +
                        "found ${timelineGetterMatches.size}: ${timelineGetterMatches.joinToString()}",
                )
            }
            val timelineGetter = timelineGetterMatches.single()
            val timelineEnumDescriptor = timelineGetter.returnType.toString()
            val repositoryTimelineGetterReference =
                "$repoDescriptor->${timelineGetter.name}()$timelineEnumDescriptor"
            val flowGetterCandidates =
                repositoryClass.methods.mapNotNull { method ->
                    val flowDescriptor = method.returnType.toString()
                    if (method.parameterTypes.isNotEmpty() ||
                        !flowDescriptor.startsWith("Lkotlinx/coroutines/flow/")
                    ) {
                        return@mapNotNull null
                    }
                    val fieldReads =
                        method.instructions.mapNotNull { instruction ->
                            if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                            instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()
                                ?.takeIf { it.definingClass.toString() == repoDescriptor }
                        }.distinctBy { "${it.definingClass}->${it.name}:${it.type}" }
                    val dataField =
                        requireAtMostOne(
                            label = "NewX URT repository data field read",
                            candidates = fieldReads,
                        ) ?: return@mapNotNull null
                    val dataFieldClass = runCatching { mutableClassDefBy(dataField.type.toString()) }.getOrNull()
                        ?: return@mapNotNull null
                    if (flowDescriptor !in dataFieldClass.interfaces.map(CharSequence::toString)) {
                        return@mapNotNull null
                    }
                    val flowClass = runCatching { mutableClassDefBy(flowDescriptor) }.getOrNull()
                        ?: return@mapNotNull null
                    val listGetters =
                        flowClass.methods.filter { candidate ->
                            candidate.parameterTypes.isEmpty() &&
                                candidate.returnType.toString() == "Ljava/util/List;"
                        }
                    val listGetter =
                        requireAtMostOne(
                            label = "NewX URT timeline data flow list getter",
                            candidates = listGetters,
                        ) ?: return@mapNotNull null
                    Triple(method, dataField, listGetter)
                }
            if (flowGetterCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX URT timeline data flow getter on $repoDescriptor, " +
                        "found ${flowGetterCandidates.size}: " +
                        flowGetterCandidates.joinToString { (method, field, _) ->
                            "${method.name}()${method.returnType} via $field"
                        },
                )
            }
            val (timelineDataGetter, _, timelineDataFlowListGetter) =
                flowGetterCandidates.single()
            val timelineDataFlowDescriptor = timelineDataGetter.returnType.toString()
            val repositoryTimelineDataGetterReference =
                "$repoDescriptor->${timelineDataGetter.name}()$timelineDataFlowDescriptor"
            val timelineDataFlowListGetterReference =
                "$timelineDataFlowDescriptor->${timelineDataFlowListGetter.name}()Ljava/util/List;"
            val eventRepositoryClass = mutableClassDefBy(requestCallReference.definingClass.toString())
            val eventTimelineDataGetterMatches =
                eventRepositoryClass.methods.filter { method ->
                    method.name == timelineDataGetter.name &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType.toString() == timelineDataFlowDescriptor
                }
            if (eventTimelineDataGetterMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT event timeline data flow getter on " +
                        "${requestCallReference.definingClass}, found ${eventTimelineDataGetterMatches.size}: " +
                        eventTimelineDataGetterMatches.joinToString(),
                )
            }
            val eventTimelineDataGetter = eventTimelineDataGetterMatches.single()
            val eventTimelineDataGetterReference =
                "${requestCallReference.definingClass}->${eventTimelineDataGetter.name}()$timelineDataFlowDescriptor"
            val repositoryAutoRefreshFieldReference =
                "$requestTypeDescriptor->AUTO_REFRESH:$requestTypeDescriptor"
            val repositoryViewportAwareAutoRefreshFieldReference =
                "$requestTypeDescriptor->VIEWPORT_AWARE_AUTO_REFRESH:$requestTypeDescriptor"
            urtRepoMatch.method.apply {
                val method = this
                val read =
                    disableTimelineRefresh.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                val settingRegister = read.register
                val requestRegister = method.p0Register + 1
                val cursorRegister = method.p0Register + 2
                // A null cursor is also used by the first request on a fresh install. Suppress
                // populated-timeline refreshes, but keep an empty initial load alive. A saved
                // position changes that load to viewport-aware refresh so it cannot jump to top.
                method.insertHook(
                    index = read.nextIndex,
                    // The setting read sits in front of the hook, so the pool must not hand its
                    // register out again for the timeline value.
                    excludedRegisters = listOf(settingRegister),
                    // The guard gates the request handler itself, so a branch onto the original
                    // first instruction has to run it instead of bypassing the toggle.
                    relocateBranchTargets = true,
                ) {
                    // The timeline value stays live across the enum comparisons.
                    val timelineRegister = scratchRegister()
                    ifEqz(settingRegister, Target.Original)
                    ifNez(cursorRegister, Target.Original)
                    sget(settingRegister, fieldReference(repositoryAutoRefreshFieldReference))
                    ifNe(requestRegister, settingRegister, Target.Original)
                    invokeVirtual(methodReference(repositoryTimelineGetterReference), method.p0Register)
                    moveResult(timelineRegister, timelineEnumDescriptor)
                    sget(
                        settingRegister,
                        fieldReference("$timelineEnumDescriptor->FOR_YOU:$timelineEnumDescriptor"),
                    )
                    ifEq(timelineRegister, settingRegister, Target.Local(URT_SUPPRESS_LABEL))
                    sget(
                        settingRegister,
                        fieldReference("$timelineEnumDescriptor->FOLLOWING:$timelineEnumDescriptor"),
                    )
                    ifEq(timelineRegister, settingRegister, Target.Local(URT_SUPPRESS_LABEL))
                    sget(
                        settingRegister,
                        fieldReference("$timelineEnumDescriptor->RANKED_FOLLOWING:$timelineEnumDescriptor"),
                    )
                    ifEq(timelineRegister, settingRegister, Target.Local(URT_SUPPRESS_LABEL))
                    goto(Target.Original)
                    label(URT_SUPPRESS_LABEL)
                    invokeStatic(methodReference(CONSUME_POST_DEEP_LINK_DESCRIPTOR))
                    moveResult(settingRegister, "Z")
                    ifNez(settingRegister, Target.Original)
                    invokeStatic(methodReference(CONSUME_FOR_YOU_FILTER_REFRESH_DESCRIPTOR))
                    moveResult(settingRegister, "Z")
                    ifNez(settingRegister, Target.Original)
                    invokeVirtual(methodReference(repositoryTimelineDataGetterReference), method.p0Register)
                    moveResult(settingRegister, timelineDataFlowDescriptor)
                    invokeInterface(methodReference(timelineDataFlowListGetterReference), settingRegister)
                    moveResult(settingRegister, LIST_DESCRIPTOR)
                    invokeStatic(methodReference(IS_TIMELINE_DATA_EMPTY_DESCRIPTOR), settingRegister)
                    moveResult(settingRegister, "Z")
                    ifNez(settingRegister, Target.Local(URT_CHECK_POSITION_LABEL))
                    returnVoid()
                    label(URT_CHECK_POSITION_LABEL)
                    invokeStatic(methodReference(RESTORE_TIMELINE_POSITION_DESCRIPTOR), timelineRegister)
                    moveResult(settingRegister, "[I")
                    ifEqz(settingRegister, Target.Original)
                    sget(requestRegister, fieldReference(repositoryViewportAwareAutoRefreshFieldReference))
                    goto(Target.Original)
                }
            }

            val settingRead =
                disableTimelineRefresh.injectRead(
                    method = autoRefreshEventMethod,
                    index = autoRefreshFieldCandidate.index,
                    excludedRegisters =
                        listOf(
                            0,
                            1,
                            autoRefreshRegister,
                            repositoryReceiverRegister,
                        ),
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val shiftedAutoRefreshFieldIndex =
                autoRefreshEventMethod.instructions.indexOfFirst { instruction ->
                    instruction.opcode == Opcode.SGET_OBJECT &&
                        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()?.let {
                            it.definingClass == autoRefreshFieldReference.definingClass &&
                                it.name == autoRefreshFieldReference.name &&
                                it.type == autoRefreshFieldReference.type
                        } == true
                }
            if (shiftedAutoRefreshFieldIndex < 0) {
                throw PatchException("NewX URT automatic-refresh request-type read moved unexpectedly")
            }
            if (settingRead.register == 0 || settingRead.register == 1) {
                throw PatchException(
                    "NewX URT automatic-refresh setting read clobbers a live local: " +
                        "v${settingRead.register}",
                )
            }
            // The setting read sits directly in front of the request call, and the guard has to run
            // for every path into it, so a branch onto the call runs the guard instead of bypassing it.
            autoRefreshEventMethod.insertHook(
                index = shiftedAutoRefreshFieldIndex + 1,
                relocateBranchTargets = true,
            ) {
                ifEqz(settingRead.register, Target.Original)
                invokeStatic(methodReference(IS_POST_DEEP_LINK_PENDING_DESCRIPTOR))
                moveResult(settingRead.register, "Z")
                ifNez(settingRead.register, Target.Original)
                invokeStatic(methodReference(IS_FOR_YOU_FILTER_REFRESH_PENDING_DESCRIPTOR))
                moveResult(settingRead.register, "Z")
                ifNez(settingRead.register, Target.Original)
                invokeInterface(methodReference(eventTimelineDataGetterReference), repositoryReceiverRegister)
                moveResult(settingRead.register, timelineDataFlowDescriptor)
                invokeInterface(methodReference(timelineDataFlowListGetterReference), settingRead.register)
                moveResult(settingRead.register, LIST_DESCRIPTOR)
                invokeStatic(methodReference(IS_TIMELINE_DATA_EMPTY_DESCRIPTOR), settingRead.register)
                moveResult(settingRead.register, "Z")
                ifNez(settingRead.register, Target.Original)
                returnVoid()
            }
        }
    }
