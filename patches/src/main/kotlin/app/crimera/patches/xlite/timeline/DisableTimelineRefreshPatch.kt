package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.XLiteSettingsCategory
import app.crimera.patches.xlite.settings.injectBooleanRead
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
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
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
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
        id = "xlite.timeline.disable_refresh",
        titleResourceName = "piko_xlite_disable_timeline_refresh_title",
        summaryResourceName = "piko_xlite_disable_timeline_refresh_summary",
        order = 100,
        defaultValue = true,
    )

private val disableTimelineRefreshSettingsPatch =
    xLiteSettingsContributionPatch {
        category(XLiteSettingsCategory.TIMELINE) {
            add(disableTimelineRefresh)
        }
    }

@Suppress("unused")
val disableTimelineRefreshPatch =
    bytecodePatch(
        name = "Disable automatic X-Lite timeline refresh",
        description = "Prevents automatic X-Lite timeline refreshes on launch and foregrounding.",
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
            lifecycleMatches.single().method.apply {
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
                val timelineResultInstruction =
                    instructions
                        .take(forYouIndex)
                        .lastOrNull { instruction ->
                            if (instruction.opcode != Opcode.MOVE_RESULT_OBJECT) return@lastOrNull false
                            val resultIndex = instruction.location.index
                            getInstruction(resultIndex - 1)
                                .getReference<MethodReference>()
                                ?.returnType == TIMELINE_TYPE_DESCRIPTOR
                        } as? OneRegisterInstruction
                        ?: throw PatchException(
                            "X-Lite auto-refresh timeline type result was not found",
                        )
                val unitLoadInstruction =
                    instructions.lastOrNull { instruction ->
                        instruction.opcode == Opcode.SGET_OBJECT &&
                            instruction.getReference<FieldReference>()?.definingClass == "Lkotlin/Unit;"
                    } ?: throw PatchException("X-Lite auto-refresh Unit return was not found")
                val originalComparisonInstruction = instructions[forYouIndex + 1]
                val timelineRegister = timelineResultInstruction.registerA
                val forYouRegister =
                    (forYouInstruction as OneRegisterInstruction).registerA
                val settingRegister =
                    findFreeRegister(
                        forYouIndex + 1,
                        listOf(timelineRegister, forYouRegister),
                    )
                val readInstructionCount =
                    disableTimelineRefresh.injectBooleanRead(
                        this,
                        forYouIndex + 1,
                        settingRegister,
                    )
                addInstructionsWithLabels(
                    forYouIndex + 1 + readInstructionCount,
                    """
                        if-eqz v$settingRegister, :piko_xlite_refresh_lifecycle_continue
                        if-eq v$timelineRegister, v$forYouRegister, :piko_xlite_refresh_lifecycle_skip
                        sget-object v$settingRegister, $TIMELINE_TYPE_DESCRIPTOR->FOLLOWING:$TIMELINE_TYPE_DESCRIPTOR
                        if-eq v$timelineRegister, v$settingRegister, :piko_xlite_refresh_lifecycle_skip
                    """.trimIndent(),
                    ExternalLabel(
                        "piko_xlite_refresh_lifecycle_continue",
                        originalComparisonInstruction,
                    ),
                    ExternalLabel(
                        "piko_xlite_refresh_lifecycle_skip",
                        unitLoadInstruction,
                    ),
                )
            }
        }
    }
