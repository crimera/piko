package app.crimera.patches.newx.settings

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.crimera.patches.common.scopedMatchAllOrNull
import app.crimera.patches.common.valueReachesRegister
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22b
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val STRING_RESOURCE_NAME = "settings_additional_resources_item_title"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val ICONS_SCOPE = "Lcom/x/icons/"
private const val ORIGINAL_LABEL = "piko_newx_settings_row_model_original"

private val GET_SETTINGS_TITLE_RES_ID_DESCRIPTOR =
    "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitleResId()I"
private val GET_SETTINGS_CLICK_HANDLER_DESCRIPTOR =
    "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()$FUNCTION0_DESCRIPTOR"

/**
 * Settings rows of the redesigned (12.33 alpha.02+) root screen are data models, not calls to the
 * basic row renderer: `title`/`subtitle` are resource-backed text models, `icon` is the app icon
 * type, and a tap runs the model's `Function0` before falling back to its navigation destination.
 *
 * Resolves the root list builder from the "Additional resources" title (an `(I)V` text-model
 * construction followed by the row model construction), derives the row model's real constructor
 * from its parameter roles, and returns it. Returns null when the target has no such builder
 * (older releases build the root list through the legacy row model that the renderer hook covers).
 */
internal class SettingsRowModelTarget(
    val method: MethodReference,
    val titleType: String,
    val titleResourceField: FieldReference,
    val titleConstructor: MethodReference,
    val iconParameterIndex: Int,
    val clickParameterIndex: Int,
    /** Kotlin default-argument mask parameter, or -1 when the constructor has none. */
    val maskParameterIndex: Int,
)

context(context: BytecodePatchContext)
internal fun resolveSettingsRowModelTarget(): SettingsRowModelTarget? {
    val titleResourceId = resourceId(ResourceType.STRING, STRING_RESOURCE_NAME)
    val builders =
        Fingerprint(
            // R8 can merge this builder outside the settings package; the resource index scopes it.
            filters = listOf(literal(titleResourceId)),
        ).scopedMatchAllOrNull().orEmpty()

    val targets =
        builders.mapNotNull { match ->
            resolveSettingsRowModelTargetInMethod(match.method, match.instructionMatches.single().index) {
                context.classDefByOrNull(it)
            }
        }
    return requireAtMostOne(
        label = "NewX settings row model target",
        candidates = targets.distinctBy { target -> target.method.toString() },
    )
}

internal fun resolveSettingsRowModelTargetInMethod(
    method: Method,
    literalIndex: Int,
    classDefinition: (String) -> ClassDef?,
): SettingsRowModelTarget? {
    val instructions = method.instructions.toList()
    val literalRegister = instructions[literalIndex].registersUsed.single()
    val titleConstructorIndex = requireAtMostOne(
        "NewX settings resource-backed title constructor in $method",
        instructions.indices.filter { index ->
            if (index <= literalIndex) return@filter false
            val instruction = instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@filter false
            instruction.opcode in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) &&
                reference.name == "<init>" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf("I") &&
                instruction.registersUsed.getOrNull(1)?.let { argument ->
                    instructions.valueReachesRegister(literalIndex, literalRegister, index, argument)
                } == true
        },
    ) ?: return null
    val titleConstructor = instructions[titleConstructorIndex].getReference<MethodReference>()!!
    val titleRegister = instructions[titleConstructorIndex].registersUsed.first()

    val invokedConstructors =
        instructions
            .mapIndexedNotNull { index, instruction ->
                instruction.getReference<MethodReference>()?.takeIf { reference ->
                    index > titleConstructorIndex &&
                    (instruction.opcode == Opcode.INVOKE_DIRECT ||
                        instruction.opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                        reference.name == "<init>" &&
                        reference.definingClass != titleConstructor.definingClass &&
                        instruction.registersUsed.getOrNull(1)?.let { argument ->
                            instructions.valueReachesRegister(titleConstructorIndex, titleRegister, index, argument)
                        } == true
                }
            }.distinctBy(MethodReference::toString)

    val titleClass =
        classDefinition(titleConstructor.definingClass) ?: return null
    val titleBaseType = titleClass.superclass ?: return null
    // The builder calls the Kotlin default-argument constructor (trailing mask `I`) or the
    // full one; the layout is judged on the parameters without the mask.
    val models =
        invokedConstructors.mapNotNull { reference ->
            val parameters = reference.parameterTypes.map(CharSequence::toString)
            val maskIndex = if (parameters.lastOrNull() == "I") parameters.lastIndex else -1
            val layout =
                rowModelLayout(
                    if (maskIndex >= 0) parameters.dropLast(1) else parameters,
                    titleBaseType,
                ) ?: return@mapNotNull null
            Triple(reference, layout, maskIndex)
        }
    requireAtMostOne(
        label = "NewX settings row model constructor in $method",
        candidates = models,
        describe = { (reference, _, _) -> reference.toString() },
    )?.let { (constructor, layout, maskIndex) ->
        val titleResourceField =
            requireExactlyOne(
                label = "NewX settings text-model resource field",
                candidates =
                    titleClass.fields.filter { field ->
                        field.type == "I" && !AccessFlags.STATIC.isSet(field.accessFlags)
                    },
            )
        return SettingsRowModelTarget(
            method = constructor,
            titleType = titleClass.type,
            titleResourceField =
                ImmutableFieldReference(titleClass.type, titleResourceField.name, "I"),
            titleConstructor =
                ImmutableMethodReference(
                    titleConstructor.definingClass,
                    titleConstructor.name,
                    titleConstructor.parameterTypes,
                    titleConstructor.returnType,
                ),
            iconParameterIndex = layout.iconIndex,
            clickParameterIndex = layout.clickIndex,
            maskParameterIndex = maskIndex,
        )
    }
    return null
}

private data class RowModelLayout(
    val iconIndex: Int,
    val clickIndex: Int,
)

/**
 * Semantic roles of the row model constructor: a title and a subtitle of the same text-model base
 * type, the app icon type, an "is selected"-style flag and the click `Function0`. Trailing
 * destination/menu parameters and Kotlin default-mask ints are not part of the contract.
 */
private fun rowModelLayout(
    parameters: List<CharSequence>,
    titleBaseType: String,
): RowModelLayout? {
    val descriptors = parameters.map(CharSequence::toString)
    if (descriptors.any { it == "J" || it == "D" }) return null
    if (descriptors.size < 6 || descriptors.take(2) != listOf(titleBaseType, titleBaseType)) return null
    if (descriptors.takeLast(1) == listOf("I")) return null
    val iconIndex = descriptors.indexOfFirst { it.startsWith(ICONS_SCOPE) }
    if (iconIndex != 2) return null
    val flagIndex = descriptors.indexOf("Z")
    val clickIndex = descriptors.indexOf(FUNCTION0_DESCRIPTOR)
    if (flagIndex < 0 || clickIndex < flagIndex) return null
    if (descriptors.count { it == FUNCTION0_DESCRIPTOR } != 1) return null
    return RowModelLayout(iconIndex, clickIndex)
}

/**
 * Rewrites the redesigned root list's "Additional resources" row model into the Piko settings
 * row at construction. Every other model passes through untouched after one int comparison.
 */
context(context: BytecodePatchContext)
internal fun patchSettingsRowModel(
    target: SettingsRowModelTarget,
    pikoIcon: (iconType: String) -> MethodReference,
) {
    val mutable =
        requireExactlyOne(
            label = "NewX settings row model constructor ${target.method}",
            candidates =
                context.mutableClassDefBy(target.method.definingClass).methods.filter { method ->
                    method.name == target.method.name &&
                        method.parameterTypes == target.method.parameterTypes
                },
        )
    val iconGetter = pikoIcon(target.method.parameterTypes[target.iconParameterIndex].toString())
    val titleResourceId = resourceId(ResourceType.STRING, STRING_RESOURCE_NAME)
    val parameterStart = mutable.p0Register + 1
    val titleParameter = parameterStart
    val iconParameter = parameterStart + target.iconParameterIndex
    val clickParameter = parameterStart + target.clickParameterIndex
    // Replaced slots must not be re-defaulted by the constructor's Kotlin default-argument mask.
    val clearedMaskBits =
        if (target.maskParameterIndex < 0) {
            0
        } else {
            listOf(0, target.iconParameterIndex, target.clickParameterIndex)
                .fold(0) { bits, index -> bits or (1 shl index) }
        }
    if (clearedMaskBits < 0 || clearedMaskBits.inv() < Byte.MIN_VALUE) {
        throw PatchException("NewX settings row model slots exceed the default mask literal range")
    }
    val maskParameter = parameterStart + target.maskParameterIndex

    // A constructor is entered with `this` uninitialized; static calls and `new-instance` of an
    // unrelated type are verifier-legal, and only parameter registers are overwritten.
    mutable.insertHook(index = 0, relocateBranchTargets = false) {
        val scratch = scratchRegister(RegisterLimit.BYTE)
        val value = scratchRegister(RegisterLimit.BYTE)
        instanceOf(scratch, titleParameter, target.titleType)
        ifEqz(scratch, Target.Local(ORIGINAL_LABEL))
        iget(scratch, titleParameter, target.titleResourceField)
        constInt(value, titleResourceId.toInt())
        ifNe(scratch, value, Target.Local(ORIGINAL_LABEL))
        newInstance(scratch, target.titleType)
        invokeStatic(methodReference(GET_SETTINGS_TITLE_RES_ID_DESCRIPTOR))
        moveResult(value, "I")
        invokeDirect(target.titleConstructor, scratch, value)
        move(titleParameter, scratch, target.titleType)
        invokeStatic(iconGetter)
        moveResult(iconParameter, iconGetter.returnType)
        invokeStatic(methodReference(GET_SETTINGS_CLICK_HANDLER_DESCRIPTOR))
        moveResult(clickParameter, FUNCTION0_DESCRIPTOR)
        if (target.maskParameterIndex >= 0) {
            add(BuilderInstruction22b(Opcode.AND_INT_LIT8, maskParameter, maskParameter, clearedMaskBits.inv()))
        }
        label(ORIGINAL_LABEL)
        nop()
    }
}
