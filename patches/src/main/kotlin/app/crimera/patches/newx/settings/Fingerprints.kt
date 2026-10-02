package app.crimera.patches.newx.settings

import app.crimera.patches.newx.utils.requireAtMostOne
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"

internal data class ComposeSettingsBasicItemLayout(
    val titleRegister: Int,
    val summaryRegister: Int,
    val iconRegister: Int,
    val clickRegister: Int,
    val iconParameterIndex: Int,
    val parameterEndRegister: Int,
)

/**
 * Resolves the stable semantic slots of the basic settings row instead of treating the Compose
 * parameter list as a version-independent ABI. Compose can add object parameters before the two
 * color longs or change the number of trailing flag ints without changing the row's role.
 */
internal fun composeSettingsBasicItemLayout(
    parameters: List<CharSequence>,
): ComposeSettingsBasicItemLayout? {
    val types = parameters.map(CharSequence::toString)
    if (types.size < 10 || types.take(2) != listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR)) {
        return null
    }

    fun uniqueIndex(
        label: String,
        indices: IntRange,
        predicate: (String) -> Boolean,
    ): Int? {
        val candidates = buildList {
            indices.forEach { index ->
                if (predicate(types[index])) add(index)
            }
        }
        return requireAtMostOne(label, candidates)
    }

    val iconIndex =
        uniqueIndex("NewX Compose settings icon parameter", 2 until types.size) { type ->
            type.startsWith("Lcom/x/icons/") && type.endsWith(';')
        } ?: return null
    val clickIndex =
        uniqueIndex("NewX Compose settings click parameter", iconIndex + 1 until types.size) { type ->
            type == FUNCTION0_DESCRIPTOR
        } ?: return null
    val modifierIndex =
        uniqueIndex("NewX Compose settings modifier parameter", clickIndex + 1 until types.size) { type ->
            type == MODIFIER_DESCRIPTOR
        } ?: return null
    val composerIndex =
        uniqueIndex("NewX Compose settings composer parameter", modifierIndex + 1 until types.size) { type ->
            type == COMPOSER_DESCRIPTOR
        } ?: return null

    requireAtMostOne(
        label = "NewX Compose settings color parameter pair",
        candidates = buildList<Int> {
            (modifierIndex + 1 until composerIndex - 1).forEach { index ->
                if (types[index] == "J" && types[index + 1] == "J") add(index)
            }
        },
    ) ?: return null

    val trailingFlags = types.drop(composerIndex + 1)
    if (trailingFlags.size < 2 || trailingFlags.any { type -> type != "I" }) {
        return null
    }

    var parameterEndRegister = -1
    val parameterRegisters = buildList {
        var register = 0
        types.forEach { type ->
            add(register)
            register += if (type == "J" || type == "D") 2 else 1
            parameterEndRegister = register - 1
        }
    }
    return ComposeSettingsBasicItemLayout(
        titleRegister = parameterRegisters[0],
        summaryRegister = parameterRegisters[1],
        iconRegister = parameterRegisters[iconIndex],
        clickRegister = parameterRegisters[clickIndex],
        iconParameterIndex = iconIndex,
        parameterEndRegister = parameterEndRegister,
    )
}

private val COMPOSE_SETTINGS_BASIC_ITEM_RENDERER_FILTERS =
    listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "Landroidx/compose/runtime/",
            parameters = listOf("I"),
            returnType = "Landroidx/compose/runtime/",
        ),
    )

/**
 * Resolves the repackaged Compose row owner from its preserved X settings caller.
 *
 * Multiple callers may share one renderer after Compose lowering. The caller path is retained for
 * older releases, but its target is narrowed by the semantic layout in SettingsPatch rather than
 * by an exact generated parameter list.
 */
internal object ComposeSettingsBasicItemCallerFingerprint : Fingerprint(
    definingClass = "Lcom/x/settings/common/",
    filters =
        listOf(
            methodCall(
                definingClass = "Lcom/x/settings/common/",
                returnType = "V",
            ),
        ),
)

/**
 * 12.28 no longer emits a caller for this renderer from the common settings package. Resolve the
 * renderer directly as a scoped fallback, then validate its parameter roles dynamically.
 */
internal object ComposeSettingsBasicItemRendererFingerprint : Fingerprint(
    definingClass = "Lcom/x/settings/common/",
    returnType = "V",
    filters = COMPOSE_SETTINGS_BASIC_ITEM_RENDERER_FILTERS,
)

/**
 * The Compose compiler may type the composer prologue result as the public Composer interface or
 * its runtime implementation. Match the stable runtime package/type shape, not generated method
 * names or optional parameter null checks.
 */
internal fun composeSettingsBasicItemFingerprint(reference: MethodReference) =
    Fingerprint(
        definingClass = reference.definingClass,
        name = reference.name,
        returnType = "V",
        parameters = reference.parameterTypes.map(CharSequence::toString),
        filters = COMPOSE_SETTINGS_BASIC_ITEM_RENDERER_FILTERS,
    )
