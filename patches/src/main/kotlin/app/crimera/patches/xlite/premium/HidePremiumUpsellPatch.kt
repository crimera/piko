package app.crimera.patches.xlite.premium

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val FEATURE_SWITCHES_DESCRIPTOR = "Lcom/x/featureswitches/f0;"
private const val SUBSCRIPTIONS_FEATURES_DESCRIPTOR = "Lcom/x/subscriptions/SubscriptionsFeatures;"

private object XLiteHomeNavUpsellTypeFingerprint : Fingerprint(
    filters =
        listOf(
            string("subscriptions_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_DESCRIPTOR,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
            string("subscriptions_upsells_home_nav_premium_tier_check_enabled"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_DESCRIPTOR,
                name = "getBoolean",
                parameters = listOf("Ljava/lang/String;", "Z"),
                returnType = "Z",
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
            string("subscriptions_upsells_premium_home_nav"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = FEATURE_SWITCHES_DESCRIPTOR,
                name = "getString",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        ),
    custom = { method, classDef ->
        SUBSCRIPTIONS_FEATURES_DESCRIPTOR in classDef.interfaces &&
            method.returnType.startsWith("L")
    },
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
            } ?: throw PatchException("X-Lite disabled home-nav upsell return was not found")

    return directSingletonReturn.first.getReference<FieldReference>()
        ?: throw PatchException("X-Lite disabled home-nav upsell field was not found")
}

@Suppress("unused")
val hidePremiumUpsellPatch =
    bytecodePatch(
        name = "X-Lite: Hide premium upsell",
        description = "Hides the premium upsell chip from the X-Lite home top bar.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hidePremiumUpsell =
            xLiteToggle(
                id = "xlite.content.hide_premium_upsell",
                category = Categories.CONTENT,
                strings = settingStrings("piko_xlite_hide_premium_upsell"),
                order = 50,
                defaultValue = true,
                rebootApp = true,
            )

        execute {
            val matches = XLiteHomeNavUpsellTypeFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite home-nav upsell checker, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            match.method.apply {
                val originalFirstInstruction = instructions.first()
                val disabledField = disabledUpsellField(match.instructionMatches.first().index)
                val disabledFieldDescriptor =
                    "${disabledField.definingClass}->${disabledField.name}:${disabledField.type}"
                val read =
                    hidePremiumUpsell.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v${read.register}, :piko_xlite_premium_upsell_continue
                        sget-object v${read.register}, $disabledFieldDescriptor
                        return-object v${read.register}
                    """.trimIndent(),
                    ExternalLabel(
                        "piko_xlite_premium_upsell_continue",
                        originalFirstInstruction,
                    ),
                )
            }
        }
    }
