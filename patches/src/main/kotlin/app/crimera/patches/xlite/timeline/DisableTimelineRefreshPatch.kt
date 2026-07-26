package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.injectBooleanRead
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.extension.xlite.api.XLiteSettings.Categories
import app.morphe.extension.xlite.api.XLiteSettings.Keys
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
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val TIMELINE_TYPE_DESCRIPTOR = "Lcom/x/models/timelines/TimelineType;"

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

private val disableTimelineRefresh =
    toggleSetting(
        key = Keys.DISABLE_TIMELINE_REFRESH,
        titleResourceName = "piko_xlite_disable_timeline_refresh_title",
        summaryResourceName = "piko_xlite_disable_timeline_refresh_summary",
        order = 100,
        defaultValue = true,
    )

private val disableTimelineRefreshSettingsPatch =
    xLiteSettingsContributionPatch {
        category(Categories.TIMELINE) {
            add(disableTimelineRefresh)
        }
    }

@Suppress("unused")
val disableTimelineRefreshPatch =
    bytecodePatch(
        name = "X-Lite: Disable automatic timeline refresh",
        description = "Prevents automatic timeline jumps on startup and foregrounding.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(disableTimelineRefreshSettingsPatch)

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
                val readInstructionCount = disableTimelineRefresh.injectBooleanRead(this, 0, 0)
                addInstructionsWithLabels(
                    readInstructionCount,
                    """
                        if-eqz v0, :piko_xlite_refresh_home_continue
                        const/4 v0, 0x0
                        return v0
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
                val forYouInstruction =
                    instructions.singleOrNull { instruction ->
                        instruction.opcode == Opcode.SGET_OBJECT &&
                            instruction.getReference<FieldReference>()?.let { reference ->
                                reference.definingClass == TIMELINE_TYPE_DESCRIPTOR &&
                                    reference.name == "FOR_YOU" &&
                                    reference.type == TIMELINE_TYPE_DESCRIPTOR
                            } == true
                    } ?: throw PatchException("X-Lite auto-refresh FOR_YOU check was not found")
                val forYouIndex = forYouInstruction.location.index
                val timelineResult =
                    instructions
                        .take(forYouIndex)
                        .lastOrNull { instruction ->
                            if (instruction.opcode != Opcode.MOVE_RESULT_OBJECT) return@lastOrNull false
                            val resultIndex = instruction.location.index
                            getInstruction(resultIndex - 1)
                                .getReference<MethodReference>()
                                ?.returnType == TIMELINE_TYPE_DESCRIPTOR
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
                    val settingRegister =
                        findFreeRegister(
                            autoRefreshIndex + 1,
                            listOf(repositoryRegister, requestTypeRegister),
                        )
                    val timelineRegister =
                        findFreeRegister(
                            autoRefreshIndex + 1,
                            listOf(repositoryRegister, requestTypeRegister, settingRegister),
                        )
                    val insertIndex = autoRefreshIndex + 1
                    val readInstructionCount =
                        disableTimelineRefresh.injectBooleanRead(
                            this,
                            insertIndex,
                            settingRegister,
                        )
                    val labelSuffix = autoRefreshLoads.size - reverseIndex
                    addInstructionsWithLabels(
                        insertIndex + readInstructionCount,
                        """
                            if-eqz v$settingRegister, :piko_xlite_refresh_lifecycle_continue_$labelSuffix
                            invoke-interface {v$repositoryRegister}, $timelineGetterReference
                            move-result-object v$timelineRegister
                            sget-object v$settingRegister, $TIMELINE_TYPE_DESCRIPTOR->FOR_YOU:$TIMELINE_TYPE_DESCRIPTOR
                            if-ne v$timelineRegister, v$settingRegister, :piko_xlite_refresh_lifecycle_check_following_$labelSuffix
                            sget-object v$requestTypeRegister, $requestTypeDescriptor->VIEWPORT_AWARE_AUTO_REFRESH:$requestTypeDescriptor
                            goto :piko_xlite_refresh_lifecycle_continue_$labelSuffix
                            :piko_xlite_refresh_lifecycle_check_following_$labelSuffix
                            sget-object v$settingRegister, $TIMELINE_TYPE_DESCRIPTOR->FOLLOWING:$TIMELINE_TYPE_DESCRIPTOR
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
