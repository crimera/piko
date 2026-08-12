package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
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
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private object XLiteHomeReselectFingerprint : Fingerprint(
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

private object XLiteLifecycleAutoRefreshFingerprint : Fingerprint(
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
    filters =
        listOf(
            fieldAccess(opcode = Opcode.SGET_OBJECT, name = "VIEWPORT_AWARE_AUTO_REFRESH"),
            fieldAccess(opcode = Opcode.SGET_OBJECT, name = "AUTO_REFRESH"),
        ),
)

@Suppress("unused")
val disableTimelineRefreshPatch =
    bytecodePatch(
        name = "X-Lite: Disable automatic timeline refresh",
        description = "Prevents automatic timeline jumps on startup and foregrounding.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val disableTimelineRefresh =
            xLiteToggle(
                id = "xlite.timeline.disable_refresh",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_xlite_disable_timeline_refresh"),
                order = 100,
                defaultValue = true,
            )

        execute {
            val homeMatches = XLiteHomeReselectFingerprint.matchAll()
            if (homeMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite home reselect handler, found ${homeMatches.size}: " +
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
                        if-eqz v${read.register}, :piko_xlite_refresh_home_continue
                        const/4 v${read.register}, 0x0
                        return v${read.register}
                    """.trimIndent(),
                    ExternalLabel("piko_xlite_refresh_home_continue", originalFirstInstruction),
                )
            }

            val lifecycleMatches = XLiteLifecycleAutoRefreshFingerprint.matchAll()
            if (lifecycleMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite lifecycle auto-refresh coroutine, found " +
                        "${lifecycleMatches.size}: " +
                        lifecycleMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val lifecycleMatch = lifecycleMatches.single()
            val requestTypeDescriptor =
                lifecycleMatch.instructionMatches.first()
                    .instruction
                    .getReference<FieldReference>()
                    ?.definingClass
                    ?: throw PatchException("X-Lite request type descriptor was not found")
            lifecycleMatch.method.apply {
                val forYouReference =
                    instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
                        instruction.getReference<FieldReference>()
                    }.singleOrNull { reference ->
                        reference.name == "FOR_YOU" &&
                            reference.type == reference.definingClass
                    } ?: throw PatchException("X-Lite auto-refresh FOR_YOU check was not found")
                val timelineTypeDescriptor = forYouReference.definingClass
                val forYouIndex =
                    instructions.single { instruction ->
                        instruction.getReference<FieldReference>() == forYouReference
                    }.location.index
                val timelineResult =
                    instructions
                        .take(forYouIndex)
                        .lastOrNull { instruction ->
                            if (instruction.opcode != Opcode.MOVE_RESULT_OBJECT) return@lastOrNull false
                            val resultIndex = instruction.location.index
                            getInstruction(resultIndex - 1)
                                .getReference<MethodReference>()
                                ?.returnType == timelineTypeDescriptor
                        } ?: throw PatchException(
                        "X-Lite auto-refresh timeline type result was not found",
                    )
                val timelineGetterCall = getInstruction(timelineResult.location.index - 1)
                val timelineGetterReference =
                    timelineGetterCall.getReference<MethodReference>()
                        ?: throw PatchException("X-Lite timeline type getter was not found")
                val timelineGetterInstruction =
                    timelineGetterCall as? FiveRegisterInstruction
                        ?: throw PatchException("X-Lite timeline type getter does not use explicit registers")
                if (timelineGetterInstruction.registerCount != 1) {
                    throw PatchException("Unexpected X-Lite timeline type getter register layout")
                }
                val autoRefreshLoads =
                    instructions.filter { instruction ->
                        instruction.opcode == Opcode.SGET_OBJECT &&
                            instruction.getReference<FieldReference>()?.name == "AUTO_REFRESH"
                    }
                if (autoRefreshLoads.size != 3) {
                    throw PatchException(
                        "Expected three X-Lite AUTO_REFRESH request loads, found " +
                            autoRefreshLoads.size,
                    )
                }
                autoRefreshLoads.asReversed().forEachIndexed { reverseIndex, autoRefreshLoad ->
                    val autoRefreshIndex = autoRefreshLoad.location.index
                    val originalNextInstruction = instructions[autoRefreshIndex + 1]
                    val requestTypeRegister =
                        (autoRefreshLoad as OneRegisterInstruction).registerA
                    val requestCall =
                        instructions
                            .drop(autoRefreshIndex + 1)
                            .firstOrNull { instruction ->
                                instruction.opcode == Opcode.INVOKE_INTERFACE &&
                                    (instruction as? FiveRegisterInstruction)?.registerCount == 3
                            } as? FiveRegisterInstruction
                            ?: throw PatchException(
                                "X-Lite auto-refresh repository call was not found",
                            )
                    val repositoryRegister = requestCall.registerC
                    val read =
                        disableTimelineRefresh.injectRead(
                            method = this,
                            index = autoRefreshIndex + 1,
                            excludedRegisters = listOf(repositoryRegister, requestTypeRegister),
                            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                        )
                    val settingRegister = read.register
                    val timelineRegister =
                        getFreeRegisterProvider(
                            autoRefreshIndex + 1,
                            1,
                            repositoryRegister,
                            requestTypeRegister,
                            settingRegister,
                        ).getFreeRegister4Bit()
                    val labelSuffix = autoRefreshLoads.size - reverseIndex
                    addInstructionsWithLabels(
                        read.nextIndex,
                        """
                            if-eqz v$settingRegister, :piko_xlite_refresh_lifecycle_continue_$labelSuffix
                            invoke-interface {v$repositoryRegister}, $timelineGetterReference
                            move-result-object v$timelineRegister
                            sget-object v$settingRegister, $timelineTypeDescriptor->FOR_YOU:$timelineTypeDescriptor
                            if-ne v$timelineRegister, v$settingRegister, :piko_xlite_refresh_lifecycle_check_following_$labelSuffix
                            sget-object v$requestTypeRegister, $requestTypeDescriptor->VIEWPORT_AWARE_AUTO_REFRESH:$requestTypeDescriptor
                            goto :piko_xlite_refresh_lifecycle_continue_$labelSuffix
                            :piko_xlite_refresh_lifecycle_check_following_$labelSuffix
                            sget-object v$settingRegister, $timelineTypeDescriptor->FOLLOWING:$timelineTypeDescriptor
                            if-ne v$timelineRegister, v$settingRegister, :piko_xlite_refresh_lifecycle_continue_$labelSuffix
                            sget-object v$requestTypeRegister, $requestTypeDescriptor->VIEWPORT_AWARE_AUTO_REFRESH:$requestTypeDescriptor
                        """.trimIndent(),
                        ExternalLabel(
                            "piko_xlite_refresh_lifecycle_continue_$labelSuffix",
                            originalNextInstruction,
                        ),
                    )
                }
            }
        }
    }
