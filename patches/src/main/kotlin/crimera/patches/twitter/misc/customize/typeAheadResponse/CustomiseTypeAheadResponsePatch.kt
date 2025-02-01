package crimera.patches.twitter.misc.customize.typeAheadResponse

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.*
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val customiseTypeAheadResponseFingerprint =
    fingerprint {
        returns("Ljava/lang/Object;")
        custom { methodDef, _ ->
            methodDef.name == "parse" && methodDef.definingClass.endsWith("JsonTypeaheadResponse\$\$JsonObjectMapper;")
        }
    }

@Suppress("unused")
val customiseTypeAheadResponsePatch =
    bytecodePatch(
        name = "Customise post font size",
        use = true,
    ) {
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(settingsPatch)

            val result by customiseTypeAheadResponseFingerprint()

            val method = result.mutableMethod

            val instructions = method.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            method.addInstructions(
                returnObj,
                """
                invoke-static {p1}, ${CUSTOMISE_DESCRIPTOR};->typeAheadResponse(Lcom/twitter/model/json/search/JsonTypeaheadResponse;)Lcom/twitter/model/json/search/JsonTypeaheadResponse;
            move-result-object p1
            """,
            )

            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("typeaheadCustomisation")
        }
    }
