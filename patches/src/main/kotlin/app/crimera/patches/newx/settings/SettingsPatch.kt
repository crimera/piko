package app.crimera.patches.newx.settings

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
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

            // ALPHA PATH: usually has one Compose settings caller.
            // BETA PATH: may have multiple callers for the same renderer; collapse them below.
            // TODO: Re-evaluate the alpha caller shape when alpha is deprecated; retain beta deduplication.
            val callerMatches =
                ComposeSettingsBasicItemCallerFingerprint.scopedMatchAll()
            if (callerMatches.isEmpty()) {
                throw PatchException("Expected at least one NewX Compose settings row caller")
            }
            val rendererReferences =
                callerMatches
                    .map { caller ->
                        caller.instructionMatches.single().instruction
                            .getReference<MethodReference>()
                            ?: throw PatchException(
                                "NewX Compose settings row call has no method reference in " +
                                    caller.originalMethod,
                            )
                    }
                    .distinctBy(MethodReference::toString)
            if (rendererReferences.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose settings row renderer across " +
                        "${callerMatches.size} callers, found ${rendererReferences.size}: " +
                        rendererReferences.joinToString(),
                )
            }
            val rendererReference = rendererReferences.single()
            val matches =
                composeSettingsBasicItemFingerprint(rendererReference)
                    .scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose settings row renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().let { match ->
                val originalMethod = match.originalMethod
                val iconType = originalMethod.parameterTypes[2].toString()
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
                        move-object/from16 v$titleRegister, p0
                        move-object/from16 v$summaryRegister, p1
                        move-object/from16 v$iconRegister, p2
                        move-object/from16 v$clickRegister, p3
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;
                        move-result-object p0
                        const/16 p1, 0x0
                        sget-object p2, $settingsIconField
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()Lkotlin/jvm/functions/Function0;
                        move-result-object p3
                        invoke-static/range {p0 .. p11}, $rendererDescriptor
                        move-object/from16 p0, v$titleRegister
                        move-object/from16 p1, v$summaryRegister
                        move-object/from16 p2, v$iconRegister
                        move-object/from16 p3, v$clickRegister
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
private fun resolveSettingsIconField(iconType: String): FieldReference {
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
