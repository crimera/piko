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
            val repositoryAutoRefreshFieldReference =
                "$requestTypeDescriptor->AUTO_REFRESH:$requestTypeDescriptor"
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
                // AUTO_REFRESH is the foreground/background refresh request. A null cursor
                // does not make it an initial load: the foreground path also uses null while
                // the restored timeline has not exposed its top cursor yet. Stop both forms.
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v$settingRegister, :piko_newx_refresh_urt_continue
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
                        return-void
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
            val eventTimelineGetterMatches =
                eventRepositoryClass.methods.filter { method ->
                    val returnType = method.returnType.toString()
                    method.parameterTypes.isEmpty() &&
                        returnType.startsWith("L") &&
                        runCatching { mutableClassDefBy(returnType).superclass == ENUM_DESCRIPTOR }.getOrDefault(false)
                }
            if (eventTimelineGetterMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX URT timeline getter on ${requestCallReference.definingClass}, " +
                        "found ${eventTimelineGetterMatches.size}: ${eventTimelineGetterMatches.joinToString()}",
                )
            }
            val eventTimelineGetter = eventTimelineGetterMatches.single()
            val eventTimelineEnumDescriptor = eventTimelineGetter.returnType.toString()
            val timelineGetterReference =
                "${requestCallReference.definingClass}->${eventTimelineGetter.name}()$eventTimelineEnumDescriptor"
            val viewportAwareAutoRefreshFieldReferenceSmali =
                "${autoRefreshFieldReference.definingClass}->VIEWPORT_AWARE_AUTO_REFRESH:$autoRefreshTypeDescriptor"
            val eventForYouFieldReference =
                "$eventTimelineEnumDescriptor->FOR_YOU:$eventTimelineEnumDescriptor"
            val eventFollowingFieldReference =
                "$eventTimelineEnumDescriptor->FOLLOWING:$eventTimelineEnumDescriptor"
            val eventRankedFollowingFieldReference =
                "$eventTimelineEnumDescriptor->RANKED_FOLLOWING:$eventTimelineEnumDescriptor"

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
            val timelineRegister =
                try {
                    autoRefreshEventMethod
                        .getFreeRegisterProvider(
                            shiftedAutoRefreshFieldIndex + 1,
                            1,
                            settingRead.register,
                            0,
                            autoRefreshRegister,
                            repositoryReceiverRegister,
                        ).getFreeRegister4Bit()
                } catch (exception: RuntimeException) {
                    throw PatchException(
                        "Could not allocate NewX URT automatic-refresh timeline register",
                        exception,
                    )
                }
            autoRefreshEventMethod.addInstructionsWithLabels(
                shiftedAutoRefreshFieldIndex + 1,
                """
                    if-eqz v${settingRead.register}, :piko_newx_refresh_event_continue
                    invoke-interface {v$repositoryReceiverRegister}, $timelineGetterReference
                    move-result-object v$timelineRegister
                    sget-object v${settingRead.register}, $eventForYouFieldReference
                    if-eq v$timelineRegister, v${settingRead.register}, :piko_newx_refresh_event_convert
                    sget-object v${settingRead.register}, $eventFollowingFieldReference
                    if-eq v$timelineRegister, v${settingRead.register}, :piko_newx_refresh_event_convert
                    sget-object v${settingRead.register}, $eventRankedFollowingFieldReference
                    if-eq v$timelineRegister, v${settingRead.register}, :piko_newx_refresh_event_convert
                    goto :piko_newx_refresh_event_continue
                    :piko_newx_refresh_event_convert
                    sget-object v$autoRefreshRegister, $viewportAwareAutoRefreshFieldReferenceSmali
                """.trimIndent(),
                ExternalLabel(
                    "piko_newx_refresh_event_continue",
                    originalRequestCall,
                ),
            )
        }
    }
