package app.crimera.patches.newx.premium

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val FEATURE_SWITCHES_SCOPE = "Lcom/x/featureswitches/"
private const val SUBSCRIPTIONS_SCOPE = "Lcom/x/subscriptions/"

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

/** Top bar header composable in Compose home tabbed scaffold. */
private object NewXHomeNavUpsellComposableFingerprint : Fingerprint(
    classFingerprint = NewXHomeTabbedScaffoldClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "L",
            "Lkotlin/jvm/functions/Function0;",
            "Lkotlin/jvm/functions/Function0;",
            "Z",
            "L",
            "Lkotlin/jvm/functions/Function0;",
            "Lkotlin/jvm/functions/Function0;",
            "Landroidx/compose/ui/Modifier;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
)

private fun MutableMethod.disabledUpsellField(startIndex: Int): FieldReference {
    val directSingletonReturn =
        instructions
            .drop(startIndex)
            .zipWithNext()
            .firstOrNull { (loadInstruction, returnInstruction) ->
                if (loadInstruction.opcode != Opcode.SGET_OBJECT) return@firstOrNull false
                if (returnInstruction.opcode != Opcode.RETURN_OBJECT) return@firstOrNull false
                val loadRegister = (loadInstruction as? OneRegisterInstruction)?.registerA
                val returnRegister = (returnInstruction as? OneRegisterInstruction)?.registerA
                loadRegister != null && loadRegister == returnRegister
            } ?: throw PatchException("NewX disabled home-nav upsell return was not found")

    return directSingletonReturn.first.getReference<FieldReference>()
        ?: throw PatchException("NewX disabled home-nav upsell field was not found")
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
            val composeMatch = NewXHomeNavUpsellComposableFingerprint.matchOrNull()
            if (composeMatch != null) {
                composeMatch.method.apply {
                    val p4Reg = p0Register + 4
                    val originalFirstInstruction = instructions.first()
                    val read =
                        hidePremiumUpsell.injectRead(
                            method = this,
                            index = 0,
                            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                        )
                    val overrideInstructions =
                        """
                            if-eqz v${read.register}, :piko_newx_premium_upsell_continue
                            const/16 v$p4Reg, 0x0
                        """.trimIndent()
                    addInstructionsWithLabels(
                        read.nextIndex,
                        overrideInstructions,
                        ExternalLabel(
                            "piko_newx_premium_upsell_continue",
                            originalFirstInstruction,
                        ),
                    )
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
                val originalFirstInstruction = instructions.first()
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
                val overrideInstructions =
                    if (disabledFieldDescriptor != null) {
                        """
                            if-eqz v${read.register}, :piko_newx_premium_upsell_continue
                            sget-object v${read.register}, $disabledFieldDescriptor
                            return-object v${read.register}
                        """.trimIndent()
                    } else {
                        """
                            if-eqz v${read.register}, :piko_newx_premium_upsell_continue
                            const/4 v${read.register}, 0x0
                            return v${read.register}
                        """.trimIndent()
                    }
                addInstructionsWithLabels(
                    read.nextIndex,
                    overrideInstructions,
                    ExternalLabel(
                        "piko_newx_premium_upsell_continue",
                        originalFirstInstruction,
                    ),
                )
            }
        }
    }
