package app.crimera.patches.newx.settings

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.cloneMutable
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val newXSettingsPatch =
    bytecodePatch(default = false) {
        dependsOn(
            newXExtensionPatch,
            newXSettingsResourcePatch,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("newx")
            prepareSettingsRegistryLoad()

            // Compose lowering may produce multiple callers for the same renderer; collapse them
            // before modifying the renderer. Newer lowering can omit the in-package caller, so
            // fall back to the scoped renderer fingerprint and keep the same semantic check.
            val callerRendererReferences =
                ComposeSettingsBasicItemCallerFingerprint
                    .scopedMatchAllOrNull()
                    .orEmpty()
                    .flatMap { caller ->
                        caller.instructionMatches.mapNotNull { instructionMatch ->
                            instructionMatch.instruction.getReference<MethodReference>()
                        }
                    }
                    .filter { reference ->
                        composeSettingsBasicItemLayout(reference.parameterTypes) != null
                    }
                    .distinctBy(MethodReference::toString)
            val callerRendererMatches =
                callerRendererReferences.flatMap { reference ->
                    composeSettingsBasicItemFingerprint(reference)
                        .scopedMatchAllOrNull()
                        .orEmpty()
                }
            val matches =
                if (callerRendererMatches.isNotEmpty()) {
                    callerRendererMatches
                } else {
                    ComposeSettingsBasicItemRendererFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty()
                        .filter { match ->
                            composeSettingsBasicItemLayout(match.originalMethod.parameterTypes) != null
                        }
                }
            val rendererMatch =
                requireExactlyOne(
                    label = "NewX Compose settings row renderer",
                    candidates = matches.distinctBy { match -> match.originalMethod.toString() },
                )

            rendererMatch.let { match ->
                val originalMethod = match.originalMethod
                val layout =
                    composeSettingsBasicItemLayout(originalMethod.parameterTypes)
                        ?: throw PatchException(
                            "NewX Compose settings row renderer has an unsupported parameter " +
                                "layout: ${originalMethod.parameterTypes}",
                        )
                val iconType = originalMethod.parameterTypes[layout.iconParameterIndex].toString()
                val settingsIconField = resolveSettingsIconField(iconType)
                val rendererDescriptor =
                    "${originalMethod.definingClass}->${originalMethod.name}(" +
                        originalMethod.parameterTypes.joinToString("") +
                        ")${originalMethod.returnType}"
                val titleRegister = match.method.findFreeRegister(0)
                val summaryRegister = match.method.findFreeRegister(0, titleRegister)
                val clickRegister =
                    match.method.findFreeRegister(0, listOf(titleRegister, summaryRegister))
                val iconRegister =
                    match.method.findFreeRegister(
                        0,
                        listOf(titleRegister, summaryRegister, clickRegister),
                    )

                match.method.addInstructionsWithLabels(
                    0,
                    """
                        invoke-static/range {p0 .. p0}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->isAdditionalResourcesTitle(Ljava/lang/String;)Z
                        move-result v$titleRegister
                        if-eqz v$titleRegister, :piko_newx_settings_original
                        move-object/from16 v$titleRegister, p${layout.titleRegister}
                        move-object/from16 v$summaryRegister, p${layout.summaryRegister}
                        move-object/from16 v$iconRegister, p${layout.iconRegister}
                        move-object/from16 v$clickRegister, p${layout.clickRegister}
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;
                        move-result-object p${layout.titleRegister}
                        const/16 p${layout.summaryRegister}, 0x0
                        sget-object p${layout.iconRegister}, $settingsIconField
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()Lkotlin/jvm/functions/Function0;
                        move-result-object p${layout.clickRegister}
                        invoke-static/range {p0 .. p${layout.parameterEndRegister}}, $rendererDescriptor
                        move-object/from16 p${layout.titleRegister}, v$titleRegister
                        move-object/from16 p${layout.summaryRegister}, v$summaryRegister
                        move-object/from16 p${layout.iconRegister}, v$iconRegister
                        move-object/from16 p${layout.clickRegister}, v$clickRegister
                        :piko_newx_settings_original
                        nop
                    """.trimIndent(),
                )
            }

            // sharedExtensionPatch finalizes after this patch and inserts Utils.setContext at
            // index zero, so registry loading always follows shared context initialization.
            newXInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static {}, $SETTINGS_REGISTRY_DESCRIPTOR->load()V",
            )
        }
    }

context(context: BytecodePatchContext)
private fun prepareSettingsRegistryLoad() {
    val registryClass = context.mutableClassDefBy(SETTINGS_REGISTRY_DESCRIPTOR)
    val loadMethod =
        registryClass.methods.singleOrNull { method ->
            method.name == "load" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "V"
        } ?: error("NewX SettingsRegistry.load() was not found")
    val registerCount = loadMethod.implementation?.registerCount ?: 0
    val preparedLoadMethod =
        if (registerCount >= SETTINGS_REGISTRATION_REGISTER_COUNT) {
            loadMethod
        } else {
            loadMethod.cloneMutable(
                additionalRegisters = SETTINGS_REGISTRATION_REGISTER_COUNT - registerCount,
            ).also { expandedMethod ->
                registryClass.methods.remove(loadMethod)
                registryClass.methods.add(expandedMethod)
            }
        }
    SettingsRegistrationState.prepare(context, preparedLoadMethod)
}

context(_: BytecodePatchContext)
internal fun resolveSettingsIconField(iconType: String): FieldReference {
    val drawableId = getResourceId(ResourceType.DRAWABLE, "ic_vector_settings_stroke")
    val fingerprint =
        Fingerprint(
            definingClass = "Lcom/x/icons/",
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(drawableId)),
        )
    val fields =
        fingerprint.scopedMatchAll().mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            match.method.instructions
                .drop(literalIndex + 1)
                .take(4)
                .firstOrNull {
                    it.opcode == Opcode.SPUT_OBJECT &&
                        it.getReference<FieldReference>()?.type == iconType
                }?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)

    if (fields.size != 1) {
        throw PatchException(
            "Expected one NewX settings icon field, found ${fields.size}: " +
                fields.joinToString(),
        )
    }
    return fields.single()
}

internal const val SETTINGS_REGISTRATION_REGISTER_COUNT = 6
