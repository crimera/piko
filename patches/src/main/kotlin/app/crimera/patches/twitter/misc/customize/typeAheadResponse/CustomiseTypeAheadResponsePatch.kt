package app.crimera.patches.twitter.misc.customize.typeAheadResponse

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseTypeAheadResponseFingerprint : Fingerprint(
    definingClass = "JsonTypeaheadResponse\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object"
)

@Suppress("unused")
val customiseTypeAheadResponsePatch =
    bytecodePatch(
        name = "Customize search suggestions",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = CustomiseTypeAheadResponseFingerprint.method

            val instructions = method.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            method.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $CUSTOMISE_DESCRIPTOR;->typeAheadResponse(Lcom/twitter/model/json/search/JsonTypeaheadResponse;)Lcom/twitter/model/json/search/JsonTypeaheadResponse;
            move-result-object p1
            """,
            )
            SettingsStatusLoadFingerprint.enableSettings("typeaheadCustomisation")
        }
    }
