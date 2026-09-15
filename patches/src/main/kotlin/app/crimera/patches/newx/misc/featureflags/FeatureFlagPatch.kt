package app.crimera.patches.newx.misc.featureflags

import app.crimera.patches.utils.scopedMatchAllOrNull
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.action
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.crimera.patches.newx.utils.requireAtMostOne
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.cloneParameters
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val STRING_TYPE = "Ljava/lang/String;"
private const val LIST_TYPE = "Ljava/util/List;"
private const val OBJECT_TYPE = "Ljava/lang/Object;"
private const val FEATURE_SWITCH_STORE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchStore;"
private const val FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchImportExport"

private const val FEATURE_SWITCHES_SCOPE = "Lcom/x/featureswitches/"

// The repository owner and accessor names are obfuscated; resolve the server-backed contract.
private object NewXFeatureSwitchRepositoryFingerprint : Fingerprint(
    definingClass = FEATURE_SWITCHES_SCOPE,
    name = "getBoolean",
    parameters = listOf(STRING_TYPE, "Z"),
    returnType = "Z",
    custom = { method, classDef ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) &&
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
            !AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            !AccessFlags.ABSTRACT.isSet(classDef.accessFlags) &&
            requireAtMostOne(
                label = "private NewX server-backed feature-switch lookup in ${classDef.type}",
                candidates = classDef.methods.privateServerLookupMethods(),
            )?.let { lookup ->
                method.hasFeatureSwitchLookup(expectedMode = true, lookupMethod = lookup)
            } == true
    },
)

private data class FeatureSwitchRepositoryResolution(
    val descriptor: String,
    val accessors: List<FeatureSwitchAccessor>,
    val lookupMethod: Method?,
)

private data class FeatureSwitchAccessor(
    val typeName: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val extensionMethod: String,
) {
    val returnOpcode =
        when {
            returnType == "J" || returnType == "D" -> Opcode.RETURN_WIDE
            returnType.startsWith("L") -> Opcode.RETURN_OBJECT
            else -> Opcode.RETURN
        }
    val moveResultOpcode =
        when {
            returnOpcode == Opcode.RETURN_WIDE -> "move-result-wide"
            returnOpcode == Opcode.RETURN_OBJECT -> "move-result-object"
            else -> "move-result"
        }
}

private val FEATURE_SWITCH_ACCESSORS =
    listOf(
        FeatureSwitchAccessor(
            "Boolean",
            listOf(STRING_TYPE, "Z"),
            "Z",
            "resolveBoolean",
        ),
        FeatureSwitchAccessor(
            "Float",
            listOf(STRING_TYPE, "F"),
            "F",
            "resolveFloat",
        ),
        FeatureSwitchAccessor(
            "Int",
            listOf(STRING_TYPE, "I"),
            "I",
            "resolveInt",
        ),
        FeatureSwitchAccessor(
            "Long",
            listOf(STRING_TYPE, "J"),
            "J",
            "resolveLong",
        ),
        FeatureSwitchAccessor(
            "Double",
            listOf(STRING_TYPE, "D"),
            "D",
            "resolveDouble",
        ),
        FeatureSwitchAccessor(
            "String",
            listOf(STRING_TYPE, STRING_TYPE),
            STRING_TYPE,
            "resolveString",
        ),
        FeatureSwitchAccessor(
            "List",
            listOf(STRING_TYPE),
            LIST_TYPE,
            "resolveList",
        ),
    )

@Suppress("unused")
val featureFlagPatch =
    bytecodePatch(
        name = "NewX: Feature switch overrides",
        description = "Adds searchable, typed NewX feature switch overrides.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSettings {
            category(Categories.ADVANCED) {
                group(Groups.FEATURE_SWITCHES) {
                    customScreen(
                        id = "newx.advanced.feature_switches.manage",
                        strings = settingStrings("piko_newx_feature_switch_manage"),
                        order = 100,
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/newx/featureswitches/FeatureSwitchFragment;",
                    )
                    action(
                        id = "newx.advanced.feature_switches.import",
                        strings = settingStrings("piko_newx_feature_switch_import"),
                        order = 200,
                        handlerClassDescriptor =
                            "$FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR\$ImportAction;",
                    )
                    action(
                        id = "newx.advanced.feature_switches.export",
                        strings = settingStrings("piko_newx_feature_switch_export"),
                        order = 300,
                        handlerClassDescriptor =
                            "$FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR\$ExportAction;",
                    )
                }
            }
        }

        execute {
            val matches = NewXFeatureSwitchRepositoryFingerprint.scopedMatchAllOrNull().orEmpty()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX feature-switch repository in $FEATURE_SWITCHES_SCOPE, found ${matches.size}: " +
                        matches.joinToString { it.originalClassDef.type },
                )
            }
            val repositoryClass = matches.single().originalClassDef
            val lookupMethods = repositoryClass.methods.privateServerLookupMethods()
            if (lookupMethods.size != 1) {
                throw PatchException(
                    "Expected one private NewX server-backed feature-switch lookup in " +
                        "${repositoryClass.type}, found ${lookupMethods.size}: " +
                        lookupMethods.joinToString(),
                )
            }
            val repositoryResolution = FeatureSwitchRepositoryResolution(
                descriptor = repositoryClass.type,
                accessors = FEATURE_SWITCH_ACCESSORS,
                lookupMethod = lookupMethods.single(),
            )
            val repository = mutableClassDefBy(repositoryResolution.descriptor)
            repositoryResolution.accessors.forEach { accessor ->
                val matches = repository.methods.matching(
                    accessor,
                    lookupMethod = repositoryResolution.lookupMethod,
                )
                requireAccessorMatches(accessor, matches)
                matches.forEach { it.cloneParameters(repository).hookReturns(accessor) }
            }
        }
    }

private fun Collection<MutableMethod>.matching(
    accessor: FeatureSwitchAccessor,
    lookupMethod: Method?,
) = filter { method ->
    AccessFlags.PUBLIC.isSet(method.accessFlags) &&
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.parameterTypes.map(CharSequence::toString) == accessor.parameterTypes &&
        method.returnType == accessor.returnType &&
        method.hasFeatureSwitchLookup(expectedMode = true, lookupMethod = lookupMethod)
}

private fun requireAccessorMatches(
    accessor: FeatureSwitchAccessor,
    methods: List<MutableMethod>,
) {
    if (methods.size == 1) return

    throw PatchException(
        "Expected NewX ${accessor.typeName.lowercase()} accessors " +
            "one semantic server-backed accessor exactly once, found ${methods.joinToString()}",
    )
}

/**
 * Repository accessors have unstable names, but retain the typed call to the repository's private
 * feature-value lookup. The literal immediately before the call distinguishes the server-backed
 * path (`1`) from the local peek path (`0`).
 */
private fun Method.hasFeatureSwitchLookup(
    expectedMode: Boolean?,
    lookupMethod: Method?,
): Boolean {
    val methodInstructions = implementation?.instructions?.toList().orEmpty()
    val lookupCalls = methodInstructions.mapIndexedNotNull { index, instruction ->
        if (
            instruction.opcode !in
                setOf(
                    Opcode.INVOKE_DIRECT,
                    Opcode.INVOKE_DIRECT_RANGE,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.INVOKE_VIRTUAL_RANGE,
                )
        ) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.parameterTypes.map(CharSequence::toString) != listOf(STRING_TYPE, "Z") ||
            reference.returnType != OBJECT_TYPE ||
            (lookupMethod != null && !reference.matches(lookupMethod))
        ) {
            return@mapIndexedNotNull null
        }

        val arguments = instruction.registersUsed
        if (arguments.size != 3 ||
            arguments[0] != p0Register ||
            arguments[1] != p0Register + 1
        ) {
            return@mapIndexedNotNull null
        }
        val modeRegister = arguments[2]
        val modeInstruction = methodInstructions.getOrNull(index - 1)
            as? OneRegisterInstruction
            ?: return@mapIndexedNotNull null
        if (modeInstruction.registerA != modeRegister || modeInstruction !is NarrowLiteralInstruction) {
            return@mapIndexedNotNull null
        }
        val mode = modeInstruction.narrowLiteral
        if (mode !in 0..1 || expectedMode != null && mode != if (expectedMode) 1 else 0) {
            return@mapIndexedNotNull null
        }
        if (methodInstructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
            return@mapIndexedNotNull null
        }
        reference
    }
    return lookupCalls.size == 1
}

private fun Iterable<Method>.privateServerLookupMethods(): List<Method> = filter { method ->
    AccessFlags.PRIVATE.isSet(method.accessFlags) &&
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.parameterTypes.map(CharSequence::toString) == listOf(STRING_TYPE, "Z") &&
        method.returnType == OBJECT_TYPE &&
        method.implementation != null
}

private fun MethodReference.matches(method: Method): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)

private fun MutableMethod.hookReturns(accessor: FeatureSwitchAccessor) {
    val returnIndices =
        instructions.indices.filter { instructions[it].opcode == accessor.returnOpcode }.asReversed()
    if (returnIndices.isEmpty()) {
        throw PatchException(
            "No ${accessor.returnOpcode} found in NewX ${accessor.typeName.lowercase()} accessor: $this",
        )
    }

    val keyRegister = p0Register + 1
    returnIndices.forEach { returnIndex ->
        val valueRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
        val lastValueRegister =
            valueRegister + if (accessor.returnOpcode == Opcode.RETURN_WIDE) 1 else 0
        if (keyRegister > 15 || lastValueRegister > 15) {
            throw PatchException(
                "NewX ${accessor.typeName.lowercase()} accessor uses registers outside " +
                    "the invoke-static 35c range: key=v$keyRegister, value=v$valueRegister",
            )
        }
        val valueArgument =
            if (accessor.returnOpcode == Opcode.RETURN_WIDE) {
                "v$valueRegister, v${valueRegister + 1}"
            } else {
                "v$valueRegister"
            }
        addInstructionsAtControlFlowLabel(
            returnIndex,
            """
                invoke-static {p1, $valueArgument}, $FEATURE_SWITCH_STORE_DESCRIPTOR->${accessor.extensionMethod}(Ljava/lang/String;${accessor.returnType})${accessor.returnType}
                ${accessor.moveResultOpcode} v$valueRegister
            """.trimIndent(),
        )
    }
}
