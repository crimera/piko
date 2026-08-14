package app.crimera.patches.xlite.misc.featureflags

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.action
import app.crimera.patches.xlite.settings.customScreen
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.cloneParameters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val STRING_TYPE = "Ljava/lang/String;"
private const val LIST_TYPE = "Ljava/util/List;"
private const val FEATURE_SWITCH_STORE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchStore;"
private const val FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchImportExport"

private const val FEATURE_SWITCH_REPOSITORY_DESCRIPTOR =
    "Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;"

private object FeatureSwitchRepositoryFingerprint : Fingerprint(
    name = "getBoolean",
    parameters = listOf(STRING_TYPE, "Z"),
    returnType = "Z",
    custom = { _, classDef ->
        classDef.type.startsWith("Lcom/x/featureswitches/") &&
            !AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            listOf(
                Triple("getFloat", listOf(STRING_TYPE, "F"), "F"),
                Triple("getInt", listOf(STRING_TYPE, "I"), "I"),
                Triple("getLong", listOf(STRING_TYPE, "J"), "J"),
                Triple("getString", listOf(STRING_TYPE, STRING_TYPE), STRING_TYPE),
            ).all { (name, parameters, returnType) ->
                classDef.methods.any { method ->
                    method.name == name &&
                        method.parameterTypes.map(CharSequence::toString) == parameters &&
                        method.returnType == returnType
                }
            }
    },
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

private fun scalarAccessor(
    typeName: String,
    valueType: String,
    extensionMethod: String,
) = FeatureSwitchAccessor(
    typeName = typeName,
    parameterTypes = listOf(STRING_TYPE, valueType),
    returnType = valueType,
    extensionMethod = extensionMethod,
)

private val FEATURE_SWITCH_ACCESSORS =
    listOf(
        scalarAccessor("Boolean", "Z", "resolveBoolean"),
        scalarAccessor("Float", "F", "resolveFloat"),
        scalarAccessor("Int", "I", "resolveInt"),
        scalarAccessor("Long", "J", "resolveLong"),
        scalarAccessor("Double", "D", "resolveDouble"),
        FeatureSwitchAccessor(
            "String",
            listOf(STRING_TYPE, STRING_TYPE),
            STRING_TYPE,
            "resolveString",
        ),
        FeatureSwitchAccessor("List", listOf(STRING_TYPE), LIST_TYPE, "resolveList"),
    )

@Suppress("unused")
val featureFlagPatch =
    bytecodePatch(
        name = "X-Lite: Feature switch overrides",
        description = "Adds searchable, typed X-Lite feature switch overrides.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.ADVANCED) {
                group(Groups.FEATURE_SWITCHES) {
                    customScreen(
                        id = "xlite.advanced.feature_switches.manage",
                        strings = settingStrings("piko_xlite_feature_switch_manage"),
                        order = 100,
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/xlite/featureswitches/FeatureSwitchFragment;",
                    )
                    action(
                        id = "xlite.advanced.feature_switches.import",
                        strings = settingStrings("piko_xlite_feature_switch_import"),
                        order = 200,
                        handlerClassDescriptor =
                            "$FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR\$ImportAction;",
                    )
                    action(
                        id = "xlite.advanced.feature_switches.export",
                        strings = settingStrings("piko_xlite_feature_switch_export"),
                        order = 300,
                        handlerClassDescriptor =
                            "$FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR\$ExportAction;",
                    )
                }
            }
        }

        execute {
            val repositoryMatches = FeatureSwitchRepositoryFingerprint.matchAll()
            val repositoryMatch =
                repositoryMatches.singleOrNull { match ->
                    match.originalClassDef.type == FEATURE_SWITCH_REPOSITORY_DESCRIPTOR
                } ?: repositoryMatches.singleOrNull()
                ?: throw PatchException(
                    "Expected one X-Lite feature-switch repository, found ${repositoryMatches.size}: " +
                        repositoryMatches.joinToString { it.originalClassDef.type },
                )
            val repository = repositoryMatch.classDef
            FEATURE_SWITCH_ACCESSORS.forEach { accessor ->
                val matches = repository.methods.matching(accessor)
                requireAccessorMatches(accessor, matches)
                matches.forEach { it.cloneParameters(repository).hookReturns(accessor) }
            }
        }
    }

private fun Collection<MutableMethod>.matching(
    accessor: FeatureSwitchAccessor,
) = filter { method ->
    AccessFlags.PUBLIC.isSet(method.accessFlags) &&
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.parameterTypes.map(CharSequence::toString) == accessor.parameterTypes &&
        method.returnType == accessor.returnType
}

private fun requireAccessorMatches(
    accessor: FeatureSwitchAccessor,
    methods: List<MutableMethod>,
) {
    if (methods.size in 1..2) return

    throw PatchException(
        "Expected one or two X-Lite ${accessor.typeName.lowercase()} accessors, " +
            "found ${methods.size}: ${methods.joinToString()}",
    )
}

private fun MutableMethod.hookReturns(accessor: FeatureSwitchAccessor) {
    val returnIndices =
        instructions.indices.filter { instructions[it].opcode == accessor.returnOpcode }.asReversed()
    if (returnIndices.isEmpty()) {
        throw PatchException(
            "No ${accessor.returnOpcode} found in X-Lite ${accessor.typeName.lowercase()} accessor: $this",
        )
    }

    val keyRegister = p0Register + 1
    returnIndices.forEach { returnIndex ->
        val valueRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
        val lastValueRegister =
            valueRegister + if (accessor.returnOpcode == Opcode.RETURN_WIDE) 1 else 0
        if (keyRegister > 15 || lastValueRegister > 15) {
            throw PatchException(
                "X-Lite ${accessor.typeName.lowercase()} accessor uses registers outside " +
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
