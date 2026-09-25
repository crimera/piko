package app.crimera.patches.newx.settings

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
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
import app.morphe.util.getReference
import app.morphe.util.p0Register
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
                val parameterStart = match.method.p0Register
                val titleParameter = parameterStart + layout.titleRegister
                val summaryParameter = parameterStart + layout.summaryRegister
                val iconParameter = parameterStart + layout.iconRegister
                val clickParameter = parameterStart + layout.clickRegister

                // The old `addInstructionsWithLabels` was a plain insertion, so labels the method
                // entry may carry stay on the original instruction: a path that branched straight
                // at the entry keeps skipping the row override.
                match.method.insertHook(
                    index = 0,
                    relocateBranchTargets = false,
                ) {
                    // The override rewrites the four object parameters in place and restores them
                    // after the recursive renderer call, so it holds four values at once. Every use
                    // is byte encodable (`move-result`/`move-object` destinations and the `if-eqz`
                    // operand), which is the range the old free-register search accepted.
                    val titleRegister = scratchRegister(RegisterLimit.BYTE)
                    val summaryRegister = scratchRegister(RegisterLimit.BYTE)
                    val clickRegister = scratchRegister(RegisterLimit.BYTE)
                    val iconRegister = scratchRegister(RegisterLimit.BYTE)

                    invokeStatic(methodReference(IS_ADDITIONAL_RESOURCES_TITLE_DESCRIPTOR), parameterStart)
                    // The hook answers a boolean, so the result is a plain `move-result`.
                    moveResult(titleRegister, "Z")
                    ifEqz(titleRegister, Target.Local(SETTINGS_ORIGINAL_LABEL))
                    move(titleRegister, titleParameter, STRING_DESCRIPTOR)
                    move(summaryRegister, summaryParameter, STRING_DESCRIPTOR)
                    move(iconRegister, iconParameter, iconType)
                    move(clickRegister, clickParameter, FUNCTION0_DESCRIPTOR)
                    invokeStatic(methodReference(GET_SETTINGS_TITLE_DESCRIPTOR))
                    moveResult(titleParameter, STRING_DESCRIPTOR)
                    constInt(summaryParameter, 0)
                    sget(iconParameter, settingsIconField)
                    invokeStatic(methodReference(GET_SETTINGS_CLICK_HANDLER_DESCRIPTOR))
                    moveResult(clickParameter, FUNCTION0_DESCRIPTOR)
                    invokeStatic(
                        methodReference(rendererDescriptor),
                        *IntArray(layout.parameterEndRegister + 1) { parameterStart + it },
                    )
                    move(titleParameter, titleRegister, STRING_DESCRIPTOR)
                    move(summaryParameter, summaryRegister, STRING_DESCRIPTOR)
                    move(iconParameter, iconRegister, iconType)
                    move(clickParameter, clickRegister, FUNCTION0_DESCRIPTOR)
                    label(SETTINGS_ORIGINAL_LABEL)
                    nop()
                }
            }

            // sharedExtensionPatch finalizes after this patch and inserts Utils.setContext at
            // index zero, so registry loading always follows shared context initialization.
            newXInitHook.fingerprint.method.insertHook(
                index = 0,
                // The old `addInstruction` was a plain insertion, so an incoming label stays on the
                // original first instruction.
                relocateBranchTargets = false,
            ) {
                invokeStatic(methodReference(SETTINGS_REGISTRY_LOAD_DESCRIPTOR))
            }
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

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val IS_ADDITIONAL_RESOURCES_TITLE_DESCRIPTOR =
    "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->isAdditionalResourcesTitle($STRING_DESCRIPTOR)Z"
private const val GET_SETTINGS_TITLE_DESCRIPTOR =
    "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()$STRING_DESCRIPTOR"
private const val GET_SETTINGS_CLICK_HANDLER_DESCRIPTOR =
    "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()$FUNCTION0_DESCRIPTOR"
private const val SETTINGS_REGISTRY_LOAD_DESCRIPTOR = "$SETTINGS_REGISTRY_DESCRIPTOR->load()V"

/** The row override's early-out label, kept from the smali block for traceability. */
private const val SETTINGS_ORIGINAL_LABEL = "piko_newx_settings_original"
