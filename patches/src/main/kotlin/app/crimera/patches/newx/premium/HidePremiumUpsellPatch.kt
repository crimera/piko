package app.crimera.patches.newx.premium

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.injectRead
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.common.hasComposeShape
import app.crimera.patches.common.parameterDescriptors
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val FEATURE_SWITCHES_SCOPE = "Lcom/x/featureswitches/"
private const val SUBSCRIPTIONS_SCOPE = "Lcom/x/subscriptions/"

private const val HOME_STATE_SCOPE = "Lcom/x/home/"
private const val HOME_TABBED_SCOPE = "Lcom/x/home/tabbed/"
private const val FUNCTION_PREFIX = "Lkotlin/jvm/functions/Function"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"

/**
 * The home-nav header's nullable state is both the sibling discriminator and the value the hook
 * nulls to skip the upsell chip. Its position is not stable (12.32 inserted a refresh-state
 * parameter before it), so it is resolved by role: the only parameter owned by the home package
 * outside its tabbed UI package. The header takes only `Function0` callbacks, which separates it
 * from the scaffold siblings that also carry the state but take `Function1`/`Function2` callbacks.
 */
private fun homeNavStateParameterIndex(parameters: List<String>): Int? {
    val callbacks = parameters.filter { it.startsWith(FUNCTION_PREFIX) }
    if (callbacks.size < 4 || callbacks.any { it != FUNCTION0_DESCRIPTOR }) return null
    return requireAtMostOne(
        label = "NewX home-nav state parameter",
        candidates =
            parameters.indices.filter { index ->
                parameters[index].startsWith(HOME_STATE_SCOPE) &&
                    !parameters[index].startsWith(HOME_TABBED_SCOPE)
            },
    )
}

private object NewXHomeNavUpsellTypeFingerprint : Fingerprint(
    definingClass = SUBSCRIPTIONS_SCOPE,
    filters =
        listOf(
            string("subscriptions_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_SCOPE,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
            string("subscriptions_upsells_home_nav_premium_tier_check_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_SCOPE,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
            string("subscriptions_upsells_premium_home_nav"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_SCOPE,
                name = "getString",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        ),
    custom = { method, classDef ->
        classDef.interfaces.any { it.startsWith(SUBSCRIPTIONS_SCOPE) } &&
            method.returnType.startsWith("L")
    },
)

/** The feature-switch interface's obfuscated short name changes between releases. */
private object NewXHomeNavUpsellEnabledFingerprint : Fingerprint(
    definingClass = SUBSCRIPTIONS_SCOPE,
    returnType = "Z",
    parameters = listOf("L"),
    filters =
        listOf(
            string("subscriptions_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
            string("subscriptions_upsells_premium_home_nav_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        ),
    custom = { _, classDef -> classDef.interfaces.any { it.startsWith(SUBSCRIPTIONS_SCOPE) } },
)

private object NewXHomeTabbedScaffoldClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/home/tabbed/",
    returnType = "V",
    filters =
        listOf(
            string("scaffold_home_tabbed"),
        ),
)

/**
 * Top bar header composable in Compose home tabbed scaffold.
 *
 * The scaffold and tab bar siblings in the same class also satisfy the generic Compose shape, and
 * the first dex-order match was silently the scaffold. The header's nullable home-nav state
 * parameter is the discriminator, and nulling that parameter is the mutation contract: the
 * header's only branch on it renders the upsell chip.
 */
private object NewXHomeNavUpsellComposableFingerprint : Fingerprint(
    classFingerprint = NewXHomeTabbedScaffoldClassFingerprint,
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterDescriptors()
        parameters.hasComposeShape(
            required =
                listOf(
                    "Landroidx/compose/ui/Modifier;",
                    "Landroidx/compose/runtime/Composer;",
                ),
            last = "I",
            objectFirst = true,
        ) &&
            homeNavStateParameterIndex(parameters) != null
    },
)

private fun MutableMethod.disabledUpsellField(startIndex: Int): FieldReference {
    val candidates =
        instructions
            .drop(startIndex)
            .zipWithNext()
            .mapIndexedNotNull { offset, (loadInstruction, returnInstruction) ->
                if (loadInstruction.opcode != Opcode.SGET_OBJECT ||
                    returnInstruction.opcode != Opcode.RETURN_OBJECT
                ) {
                    return@mapIndexedNotNull null
                }
                val loadRegister = (loadInstruction as? OneRegisterInstruction)?.registerA
                val returnRegister = (returnInstruction as? OneRegisterInstruction)?.registerA
                if (loadRegister == null || loadRegister != returnRegister) {
                    return@mapIndexedNotNull null
                }
                val field = loadInstruction.getReference<FieldReference>()
                    ?: return@mapIndexedNotNull null
                field to (startIndex + offset)
            }
    val fields = candidates.map { it.first }.distinctBy(FieldReference::toString)
    if (fields.size != 1) {
        val descriptions =
            candidates
                .joinToString { (field, index) -> "$field at instruction $index" }
                .ifEmpty { "<none>" }
        throw PatchException(
            "Expected exactly one NewX disabled home-nav upsell field after instruction $startIndex, " +
                "found ${fields.size}: $descriptions",
        )
    }

    return fields.single()
}

@Suppress("unused")
val hidePremiumUpsellPatch =
    bytecodePatch(
        name = "NewX: Hide premium upsell",
        description = "Hides the premium upsell chip from the NewX home top bar.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hidePremiumUpsell =
            newXToggle(
                id = "newx.content.hide_premium_upsell",
                category = Categories.CONTENT,
                strings = settingStrings("piko_newx_hide_premium_upsell"),
                order = 200,
                defaultValue = true,
                rebootApp = true,
            )

        execute {
            val composeMatches = NewXHomeNavUpsellComposableFingerprint.scopedMatchAllOrNull().orEmpty()
            if (composeMatches.isNotEmpty()) {
                val composeMatch =
                    requireExactlyOne(
                        label = "NewX home-nav upsell composable",
                        candidates = composeMatches,
                    )
                composeMatch.method.apply {
                    // The fingerprint guarantees this register holds the nullable home-nav state.
                    val stateIndex =
                        homeNavStateParameterIndex(parameterDescriptors())
                            ?: throw PatchException("NewX home-nav upsell composable lost its state parameter")
                    val stateRegister =
                        p0Register +
                            parameterDescriptors().take(stateIndex).sumOf { descriptor ->
                                if (descriptor == "J" || descriptor == "D") 2 else 1 as Int
                            }
                    val read =
                        hidePremiumUpsell.injectRead(
                            method = this,
                            index = 0,
                            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                        )
                    insertHook(index = read.nextIndex, relocateBranchTargets = false) {
                        ifEqz(read.register, Target.Original)
                        constInt(stateRegister, 0)
                    }
                }
                return@execute
            }

            val typeMatches = NewXHomeNavUpsellTypeFingerprint.scopedMatchAllOrNull().orEmpty()
            val enabledMatches = NewXHomeNavUpsellEnabledFingerprint.scopedMatchAllOrNull().orEmpty()
            val matches = typeMatches + enabledMatches
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX home-nav upsell checker, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            match.method.apply {
                val disabledFieldDescriptor =
                    if (match in typeMatches) {
                        disabledUpsellField(match.instructionMatches.first().index).let { field ->
                            "${field.definingClass}->${field.name}:${field.type}"
                        }
                    } else {
                        null
                    }
                val read =
                    hidePremiumUpsell.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                val disabledField =
                    disabledFieldDescriptor?.let { descriptor -> fieldReference(descriptor) }
                insertHook(index = read.nextIndex, relocateBranchTargets = false) {
                    ifEqz(read.register, Target.Original)
                    if (disabledField != null) {
                        sget(read.register, disabledField)
                        returnObject(read.register)
                    } else {
                        constInt(read.register, 0)
                        returnValue(read.register)
                    }
                }
            }
        }
    }
