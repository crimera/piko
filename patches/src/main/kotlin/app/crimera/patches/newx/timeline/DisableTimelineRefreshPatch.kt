package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
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
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

private object NewXLifecycleRefreshLaunchFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    name = "onResume",
    parameters = emptyList(),
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                parameters = listOf("L", "L", "L", "L", "I"),
                returnType = "L",
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

            val lifecycleMatches = NewXLifecycleRefreshLaunchFingerprint.scopedMatchAll()
            if (lifecycleMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX lifecycle refresh launch, found ${lifecycleMatches.size}: " +
                        lifecycleMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val lifecycleMatch = lifecycleMatches.single()
            val lifecycleDescriptor = lifecycleMatch.originalMethod.definingClass
            val timelineGetter =
                lifecycleMatch.method.instructions
                    .mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@mapNotNull null
                        instruction.getReference<MethodReference>()
                    }
                    .singleOrNull { reference ->
                        reference.name == "a" &&
                            reference.parameterTypes.isEmpty() &&
                            reference.returnType.toString() == "Lcom/x/models/timelines/v;"
                    } ?: throw PatchException("NewX lifecycle timeline getter was not found")
            val controllerRead =
                lifecycleMatch.method.instructions
                    .mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                        if (field.definingClass.toString() != lifecycleDescriptor) {
                            return@mapNotNull null
                        }
                        val registers = instruction as? TwoRegisterInstruction
                            ?: return@mapNotNull null
                        field to registers.registerA
                    }
                    .singleOrNull()
                    ?: throw PatchException("NewX lifecycle controller field was not found")
            val controllerField = controllerRead.first
            val controllerRegister = controllerRead.second
            val controllerDescriptor = controllerField.type.toString()
            val repositoryField =
                mutableClassDefBy(controllerDescriptor).fields
                    .singleOrNull { field ->
                        field.type.toString() == timelineGetter.definingClass.toString()
                    } ?: throw PatchException("NewX lifecycle timeline repository field was not found")
            val repositoryFieldDescriptor =
                "$controllerDescriptor->${repositoryField.name}:${repositoryField.type}"
            val timelineGetterDescriptor =
                "${timelineGetter.definingClass}->${timelineGetter.name}(" +
                    timelineGetter.parameterTypes.joinToString("") + ")${timelineGetter.returnType}"
            val refreshLaunchIndex = lifecycleMatch.instructionMatches.single().index
            val controllerReadIndex =
                lifecycleMatch.method.instructions.indexOfFirst { instruction ->
                    instruction.opcode == Opcode.IGET_OBJECT &&
                        instruction.getReference<FieldReference>() == controllerField
                }
            if (controllerReadIndex < 0 || controllerReadIndex >= refreshLaunchIndex) {
                throw PatchException("NewX lifecycle controller read precedes no refresh launch")
            }
            val lifecycleGuardIndex = controllerReadIndex + 1
            lifecycleMatch.method.apply {
                val originalNextInstruction = instructions[lifecycleGuardIndex]
                val timelineRegister =
                    getFreeRegisterProvider(lifecycleGuardIndex, 1, controllerRegister)
                        .getFreeRegister4Bit()
                val read =
                    disableTimelineRefresh.injectRead(
                        method = this,
                        index = lifecycleGuardIndex,
                        excludedRegisters = listOf(controllerRegister, timelineRegister),
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v${read.register}, :piko_newx_refresh_lifecycle_continue
                        iget-object v$timelineRegister, v$controllerRegister, $repositoryFieldDescriptor
                        invoke-interface {v$timelineRegister}, $timelineGetterDescriptor
                        move-result-object v$timelineRegister
                        sget-object v${read.register}, Lcom/x/models/timelines/v;->FOR_YOU:Lcom/x/models/timelines/v;
                        if-eq v$timelineRegister, v${read.register}, :piko_newx_refresh_lifecycle_return
                        sget-object v${read.register}, Lcom/x/models/timelines/v;->FOLLOWING:Lcom/x/models/timelines/v;
                        if-eq v$timelineRegister, v${read.register}, :piko_newx_refresh_lifecycle_return
                        goto :piko_newx_refresh_lifecycle_continue
                        :piko_newx_refresh_lifecycle_return
                        return-void
                    """.trimIndent(),
                    ExternalLabel(
                        "piko_newx_refresh_lifecycle_continue",
                        originalNextInstruction,
                    ),
                )
            }
        }
    }
