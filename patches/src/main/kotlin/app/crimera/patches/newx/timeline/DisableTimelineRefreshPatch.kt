package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineScrollPositionStore;"
private const val TIMELINE_REFRESH_GATE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineRefreshGate;"

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

private object NewXUrtRepositoryRequestFingerprint : Fingerprint(
    definingClass = "Lcom/x/repositories/urt/",
    parameters = listOf("L", "L"),
    returnType = "V",
    filters = listOf(string("requestType")),
)

private object NewXUrtAutoRefreshEventFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    parameters = listOf("L"),
    returnType = "V",
    strings = listOf("event"),
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "AUTO_REFRESH",
                type = "L",
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Lcom/x/repositories/urt/",
                parameters = listOf("L", "L"),
                returnType = "V",
            ),
        ),
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
                val intentRegister = getFreeRegisterProvider(0, 1).getFreeRegister4Bit()
                addInstructions(
                    0,
                    """
                        invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                        move-result-object v$intentRegister
                        invoke-static {v$intentRegister}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->markPostDeepLink(Landroid/content/Intent;)V
                    """.trimIndent(),
                )
            }

            val mainActivityOnNewIntentMatches = NewXMainActivityOnNewIntentFingerprint.scopedMatchAll()
            if (mainActivityOnNewIntentMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX MainActivity onNewIntent method, found " +
                        "${mainActivityOnNewIntentMatches.size}: " +
                        mainActivityOnNewIntentMatches.joinToString { it.originalMethod.toString() },
                )
            }
            mainActivityOnNewIntentMatches.single().method.addInstructions(
                0,
                "invoke-static {p1}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->markPostDeepLink(Landroid/content/Intent;)V",
            )

            val homeMatches = NewXHomeReselectFingerprint.scopedMatchAll()
            if (homeMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX home reselect handler, found ${homeMatches.size}: " +
                        homeMatches.joinToString { it.originalMethod.toString() },
                )
            }
            homeMatches.single().method.apply {
                val originalFirstInstruction = instructions.first()
                val read =
                    disableTimelineRefresh.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v${read.register}, :piko_newx_refresh_home_continue
                        const/4 v${read.register}, 0x0
                        return v${read.register}
                    """.trimIndent(),
                    ExternalLabel("piko_newx_refresh_home_continue", originalFirstInstruction),
                )
            }

            val urtRepoMatches = NewXUrtRepositoryRequestFingerprint.scopedMatchAll()
            if (urtRepoMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT repository request handler, found ${urtRepoMatches.size}: " +
                        urtRepoMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val urtRepoMatch = urtRepoMatches.single()
            val repoDescriptor = urtRepoMatch.originalMethod.definingClass
            val requestTypeDescriptor =
                urtRepoMatch.originalMethod.parameterTypes.firstOrNull()?.toString()?.takeIf { it.startsWith("L") }
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
                    if (fieldReads.size != 1) return@mapNotNull null
                    val dataField = fieldReads.single()
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
                    if (listGetters.size != 1) return@mapNotNull null
                    Triple(method, dataField, listGetters.single())
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
            val repositoryAutoRefreshFieldReference =
                "$requestTypeDescriptor->AUTO_REFRESH:$requestTypeDescriptor"
            val repositoryViewportAwareAutoRefreshFieldReference =
                "$requestTypeDescriptor->VIEWPORT_AWARE_AUTO_REFRESH:$requestTypeDescriptor"
            urtRepoMatch.method.apply {
                val originalFirstInstruction = instructions.first()
                val read =
                    disableTimelineRefresh.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                val settingRegister = read.register
                val timelineRegister =
                    getFreeRegisterProvider(
                        0,
                        1,
                        settingRegister,
                    ).getFreeRegister4Bit()
                // A null cursor is also used by the first request on a fresh install. Suppress
                // populated-timeline refreshes, but keep an empty initial load alive. A saved
                // position changes that load to viewport-aware refresh so it cannot jump to top.
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v$settingRegister, :piko_newx_refresh_urt_continue
                        if-nez p2, :piko_newx_refresh_urt_continue
                        sget-object v$settingRegister, $repositoryAutoRefreshFieldReference
                        if-ne p1, v$settingRegister, :piko_newx_refresh_urt_continue
                        invoke-virtual {p0}, $repositoryTimelineGetterReference
                        move-result-object v$timelineRegister
                        sget-object v$settingRegister, $timelineEnumDescriptor->FOR_YOU:$timelineEnumDescriptor
                        if-eq v$timelineRegister, v$settingRegister, :piko_newx_refresh_urt_suppress
                        sget-object v$settingRegister, $timelineEnumDescriptor->FOLLOWING:$timelineEnumDescriptor
                        if-eq v$timelineRegister, v$settingRegister, :piko_newx_refresh_urt_suppress
                        sget-object v$settingRegister, $timelineEnumDescriptor->RANKED_FOLLOWING:$timelineEnumDescriptor
                        if-eq v$timelineRegister, v$settingRegister, :piko_newx_refresh_urt_suppress
                        goto :piko_newx_refresh_urt_continue
                        :piko_newx_refresh_urt_suppress
                        invoke-static {}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->consumePostDeepLink()Z
                        move-result v$settingRegister
                        if-nez v$settingRegister, :piko_newx_refresh_urt_continue
                        invoke-static {}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->consumeForYouFilterRefresh()Z
                        move-result v$settingRegister
                        if-nez v$settingRegister, :piko_newx_refresh_urt_continue
                        invoke-virtual {p0}, $repositoryTimelineDataGetterReference
                        move-result-object v$settingRegister
                        invoke-interface {v$settingRegister}, $timelineDataFlowListGetterReference
                        move-result-object v$settingRegister
                        invoke-static {v$settingRegister}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->isTimelineDataEmpty(Ljava/util/List;)Z
                        move-result v$settingRegister
                        if-nez v$settingRegister, :piko_newx_refresh_urt_check_position
                        return-void
                        :piko_newx_refresh_urt_check_position
                        invoke-static {v$timelineRegister}, $TIMELINE_POSITION_STORE_DESCRIPTOR->restore($ENUM_DESCRIPTOR)[I
                        move-result-object v$settingRegister
                        if-eqz v$settingRegister, :piko_newx_refresh_urt_continue
                        sget-object p1, $repositoryViewportAwareAutoRefreshFieldReference
                        goto :piko_newx_refresh_urt_continue
                    """.trimIndent(),
                    ExternalLabel(
                        "piko_newx_refresh_urt_continue",
                        originalFirstInstruction,
                    ),
                )
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
                    reference.definingClass.startsWith("Lcom/x/repositories/urt/") &&
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
            val originalRequestCall =
                autoRefreshEventMethod.instructions.getOrNull(shiftedAutoRefreshFieldIndex + 1)
                    ?: throw PatchException("NewX URT automatic-refresh request call continuation was not found")
            autoRefreshEventMethod.addInstructionsWithLabels(
                shiftedAutoRefreshFieldIndex + 1,
                """
                    if-eqz v${settingRead.register}, :piko_newx_refresh_event_continue
                    invoke-static {}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->isPostDeepLinkPending()Z
                    move-result v${settingRead.register}
                    if-nez v${settingRead.register}, :piko_newx_refresh_event_continue
                    invoke-static {}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->isForYouFilterRefreshPending()Z
                    move-result v${settingRead.register}
                    if-nez v${settingRead.register}, :piko_newx_refresh_event_continue
                    invoke-interface {v$repositoryReceiverRegister}, $eventTimelineDataGetterReference
                    move-result-object v${settingRead.register}
                    invoke-interface {v${settingRead.register}}, $timelineDataFlowListGetterReference
                    move-result-object v${settingRead.register}
                    invoke-static {v${settingRead.register}}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->isTimelineDataEmpty(Ljava/util/List;)Z
                    move-result v${settingRead.register}
                    if-nez v${settingRead.register}, :piko_newx_refresh_event_continue
                    return-void
                """.trimIndent(),
                ExternalLabel(
                    "piko_newx_refresh_event_continue",
                    originalRequestCall,
                ),
            )
        }
    }
