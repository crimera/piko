package app.crimera.patches.xlite.misc.featureflags

import app.crimera.patches.utils.scopedMatchAllOrNull
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

// ALPHA PATH: preserved repository owner in 12.17.3-alpha.01.
// TODO: Remove this repository and its accessor table when alpha compatibility is deprecated.
private const val FEATURE_SWITCH_REPOSITORY_DESCRIPTOR =
    "Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;"

private const val FEATURE_SWITCHES_SCOPE = "Lcom/x/featureswitches/"

// BETA PATH: repository fingerprint scoped to com/x/featureswitches/.
private object XLiteFeatureSwitchRepositoryFingerprint : Fingerprint(
    definingClass = FEATURE_SWITCHES_SCOPE,
    name = "getBoolean",
    parameters = listOf(STRING_TYPE, "Z"),
    returnType = "Z",
    custom = { method, classDef ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) &&
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
            !AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            !AccessFlags.ABSTRACT.isSet(classDef.accessFlags)
    },
)

private data class FeatureSwitchAccessor(
    val typeName: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val extensionMethod: String,
    val methodNames: List<String> = listOf("get$typeName", "peek$typeName"),
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

// ALPHA PATH: named accessors from FeatureSwitchesRepositoryImpl.
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

// BETA PATH: obfuscated repository accessors from 12.18.0-beta.0 and later.
private val BETA_FEATURE_SWITCH_ACCESSORS =
    listOf(
        FeatureSwitchAccessor("Boolean", listOf(STRING_TYPE, "Z"), "Z", "resolveBoolean", listOf("getBoolean")),
        FeatureSwitchAccessor("Float", listOf(STRING_TYPE, "F"), "F", "resolveFloat", listOf("getFloat")),
        FeatureSwitchAccessor("Int", listOf(STRING_TYPE, "I"), "I", "resolveInt", listOf("getInt")),
        FeatureSwitchAccessor("Long", listOf(STRING_TYPE, "J"), "J", "resolveLong", listOf("getLong")),
        FeatureSwitchAccessor("Double", listOf(STRING_TYPE, "D"), "D", "resolveDouble", listOf("a")),
        FeatureSwitchAccessor(
            "String",
            listOf(STRING_TYPE, STRING_TYPE),
            STRING_TYPE,
            "resolveString",
            listOf("getString"),
        ),
        FeatureSwitchAccessor("List", listOf(STRING_TYPE), LIST_TYPE, "resolveList", listOf("d")),
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
            val isAlpha = classDefByOrNull(FEATURE_SWITCH_REPOSITORY_DESCRIPTOR) != null
            val (repositoryDescriptor, accessors) =
                if (isAlpha) {
                    FEATURE_SWITCH_REPOSITORY_DESCRIPTOR to FEATURE_SWITCH_ACCESSORS
                } else {
                    val matches =
                        XLiteFeatureSwitchRepositoryFingerprint.scopedMatchAllOrNull().orEmpty()
                    if (matches.size != 1) {
                        throw PatchException(
                            "Expected one X-Lite feature-switch repository in $FEATURE_SWITCHES_SCOPE, found ${matches.size}: " +
                                matches.joinToString { it.originalClassDef.type },
                        )
                    }
                    matches.single().originalClassDef.type to BETA_FEATURE_SWITCH_ACCESSORS
                }
            val repository = mutableClassDefBy(repositoryDescriptor)
            accessors.forEach { accessor ->
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
        method.name in accessor.methodNames &&
        method.parameterTypes.map(CharSequence::toString) == accessor.parameterTypes &&
        method.returnType == accessor.returnType
}

private fun requireAccessorMatches(
    accessor: FeatureSwitchAccessor,
    methods: List<MutableMethod>,
) {
    val actualNames = methods.map(MutableMethod::getName).toSet()
    if (methods.size == accessor.methodNames.size && actualNames == accessor.methodNames.toSet()) return

    throw PatchException(
        "Expected X-Lite ${accessor.typeName.lowercase()} accessors " +
            "${accessor.methodNames.joinToString()} exactly once, found ${methods.joinToString()}",
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
