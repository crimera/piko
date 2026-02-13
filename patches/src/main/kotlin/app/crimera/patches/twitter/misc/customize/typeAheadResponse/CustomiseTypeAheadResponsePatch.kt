package app.crimera.patches.twitter.misc.customize.typeAheadResponse

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val customiseTypeAheadResponseFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        custom { methodDef, _ ->
            methodDef.name == "parse" && methodDef.definingClass.endsWith("JsonTypeaheadResponse\$\$JsonObjectMapper;")
        }
    }

@Suppress("unused")
val customiseTypeAheadResponsePatch =
    bytecodePatch(
        name = "Customize search suggestions",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = customiseTypeAheadResponseFingerprint.method

            val instructions = method.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            method.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $CUSTOMISE_DESCRIPTOR;->typeAheadResponse(Lcom/twitter/model/json/search/JsonTypeaheadResponse;)Lcom/twitter/model/json/search/JsonTypeaheadResponse;
            move-result-object p1
            """,
            )
            settingsStatusLoadFingerprint.enableSettings("typeaheadCustomisation")
        }
    }
