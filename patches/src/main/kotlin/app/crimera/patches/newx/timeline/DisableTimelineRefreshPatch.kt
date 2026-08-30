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
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import com.android.tools.smali.dexlib2.Opcode

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
    name = "i",
    parameters = listOf("Lcom/x/models/timelines/d;", "Lcom/x/models/timelines/items/j0;"),
    returnType = "V",
    filters = listOf(string("requestType")),
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
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v$settingRegister, :piko_newx_refresh_urt_continue
                        if-nez p2, :piko_newx_refresh_urt_continue
                        sget-object v$settingRegister, Lcom/x/models/timelines/d;->AUTO_REFRESH:Lcom/x/models/timelines/d;
                        if-ne p1, v$settingRegister, :piko_newx_refresh_urt_continue
                        iget-object v$timelineRegister, p0, $repoDescriptor->a:Lcom/x/models/timelines/v;
                        sget-object v$settingRegister, Lcom/x/models/timelines/v;->FOR_YOU:Lcom/x/models/timelines/v;
                        if-eq v$timelineRegister, v$settingRegister, :piko_newx_refresh_urt_convert
                        sget-object v$settingRegister, Lcom/x/models/timelines/v;->FOLLOWING:Lcom/x/models/timelines/v;
                        if-eq v$timelineRegister, v$settingRegister, :piko_newx_refresh_urt_convert
                        goto :piko_newx_refresh_urt_continue
                        :piko_newx_refresh_urt_convert
                        sget-object p1, Lcom/x/models/timelines/d;->VIEWPORT_AWARE_AUTO_REFRESH:Lcom/x/models/timelines/d;
                    """.trimIndent(),
                    ExternalLabel(
                        "piko_newx_refresh_urt_continue",
                        originalFirstInstruction,
                    ),
                )
            }
        }
    }
