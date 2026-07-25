package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.XLiteSettingsCategory
import app.crimera.patches.xlite.settings.injectBooleanRead
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
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
import com.android.tools.smali.dexlib2.Opcode

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
                val originalFirstInstruction = instructions.first()
                val readInstructionCount = disableTimelineRefresh.injectBooleanRead(this, 0, 0)
                addInstructionsWithLabels(
                    readInstructionCount,
                    """
                        if-eqz v0, :piko_xlite_refresh_lifecycle_continue
                        return-object p1
                    """.trimIndent(),
                    ExternalLabel("piko_xlite_refresh_lifecycle_continue", originalFirstInstruction),
                )
            }
        }
    }
