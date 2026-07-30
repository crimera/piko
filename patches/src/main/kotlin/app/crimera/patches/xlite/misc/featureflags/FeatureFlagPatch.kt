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
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.cloneParameters
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val STRING_TYPE = "Ljava/lang/String;"
private const val LIST_TYPE = "Ljava/util/List;"
private const val FEATURE_SWITCH_STORE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchStore;"
private const val FEATURE_SWITCH_IMPORT_EXPORT_DESCRIPTOR =
    "$EXTENSION_PACKAGE/featureswitches/FeatureSwitchImportExport"

private object XLiteFeatureSwitchRepositoryFingerprint : Fingerprint(
    parameters = emptyList(),
    returnType = "J",
    filters = listOf(string("android_system_dns_timeout_ms")),
    custom = { _, classDef ->
        classDef.type.startsWith("Lcom/x/featureswitches/") &&
            classDef.methods.any {
            it.name == "getBoolean" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(STRING_TYPE, "Z") &&
                it.returnType == "Z"
        } &&
            classDef.methods.any {
                it.name == "getString" &&
                    it.parameterTypes.map(CharSequence::toString) ==
                    listOf(STRING_TYPE, STRING_TYPE) &&
                    it.returnType == STRING_TYPE
            }
    },
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
            val repositoryMatches = XLiteFeatureSwitchRepositoryFingerprint.matchAll()
            if (repositoryMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite feature switch repository, found " +
                        "${repositoryMatches.size}: " +
                        repositoryMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val repository =
                mutableClassDefBy(repositoryMatches.single().originalMethod.definingClass)
            val methods = repository.methods

            val booleanMethods = methods.withSignature(listOf(STRING_TYPE, "Z"), "Z")
            val floatMethods = methods.withSignature(listOf(STRING_TYPE, "F"), "F")
            val intMethods = methods.withSignature(listOf(STRING_TYPE, "I"), "I")
            val longMethods = methods.withSignature(listOf(STRING_TYPE, "J"), "J")
            val doubleMethods = methods.withSignature(listOf(STRING_TYPE, "D"), "D")
            val stringMethods = methods.withSignature(listOf(STRING_TYPE, STRING_TYPE), STRING_TYPE)
            val listMethods = methods.withSignature(listOf(STRING_TYPE), LIST_TYPE)

            requireMethodCount("boolean", booleanMethods, 2)
            requireMethodCount("float", floatMethods, 1)
            requireMethodCount("int", intMethods, 1)
            requireMethodCount("long", longMethods, 1)
            requireMethodCount("double", doubleMethods, 1)
            requireMethodCount("string", stringMethods, 1)
            requireMethodCount("list", listMethods, 1)

            val hookedBooleanMethods = booleanMethods.map { it.cloneParameters(repository) }
            val hookedFloatMethod = floatMethods.single().cloneParameters(repository)
            val hookedIntMethod = intMethods.single().cloneParameters(repository)
            val hookedLongMethod = longMethods.single().cloneParameters(repository)
            val hookedDoubleMethod = doubleMethods.single().cloneParameters(repository)
            val hookedStringMethod = stringMethods.single().cloneParameters(repository)
            val hookedListMethod = listMethods.single().cloneParameters(repository)

            hookedBooleanMethods.forEach {
                it.hookReturns(Opcode.RETURN, "resolveBoolean", "Z", "move-result")
            }
            hookedFloatMethod.hookReturns(Opcode.RETURN, "resolveFloat", "F", "move-result")
            hookedIntMethod.hookReturns(Opcode.RETURN, "resolveInt", "I", "move-result")
            hookedLongMethod.hookReturns(
                Opcode.RETURN_WIDE,
                "resolveLong",
                "J",
                "move-result-wide",
            )
            hookedDoubleMethod.hookReturns(
                Opcode.RETURN_WIDE,
                "resolveDouble",
                "D",
                "move-result-wide",
            )
            hookedStringMethod.hookReturns(
                Opcode.RETURN_OBJECT,
                "resolveString",
                STRING_TYPE,
                "move-result-object",
            )
            hookedListMethod.hookReturns(
                Opcode.RETURN_OBJECT,
                "resolveList",
                LIST_TYPE,
                "move-result-object",
            )
        }
    }

private fun Collection<MutableMethod>.withSignature(
    parameters: List<String>,
    returnType: String,
) = filter {
    it.parameterTypes.map(CharSequence::toString) == parameters && it.returnType == returnType
}

private fun requireMethodCount(
    type: String,
    methods: List<MutableMethod>,
    expectedCount: Int,
) {
    if (methods.size == expectedCount) return
    throw PatchException(
        "Expected $expectedCount X-Lite $type feature switch getter(s), found ${methods.size}: " +
            methods.joinToString(),
    )
}

private fun MutableMethod.hookReturns(
    returnOpcode: Opcode,
    extensionMethod: String,
    valueType: String,
    moveResultOpcode: String,
) {
    val returnIndices =
        instructions.indices.filter { instructions[it].opcode == returnOpcode }.asReversed()
    if (returnIndices.isEmpty()) {
        throw PatchException("No $returnOpcode found in X-Lite feature switch getter: $this")
    }

    returnIndices.forEach { returnIndex ->
        val valueRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
        val valueArgument =
            if (returnOpcode == Opcode.RETURN_WIDE) {
                "v$valueRegister, v${valueRegister + 1}"
            } else {
                "v$valueRegister"
            }
        addInstructionsAtControlFlowLabel(
            returnIndex,
            """
                invoke-static {p1, $valueArgument}, $FEATURE_SWITCH_STORE_DESCRIPTOR->$extensionMethod(Ljava/lang/String;$valueType)$valueType
                $moveResultOpcode v$valueRegister
            """.trimIndent(),
        )
    }
}
