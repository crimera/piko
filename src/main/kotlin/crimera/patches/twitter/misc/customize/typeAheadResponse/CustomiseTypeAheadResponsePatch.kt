package crimera.patches.twitter.misc.customize.typeAheadResponse

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseTypeAheadResponseFingerprint : MethodFingerprint(
    returnType = "Ljava/lang/Object",
    customFingerprint = { methodDef, _ ->
        methodDef.name == "parse" && methodDef.definingClass.endsWith("JsonTypeaheadResponse\$\$JsonObjectMapper;")
    },
)

@Patch(
    name = "Customize search suggestions",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object CustomiseTypeAheadResponsePatch : BytecodePatch(
    setOf(CustomiseTypeAheadResponseFingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result =
            CustomiseTypeAheadResponseFingerprint.result
                ?: throw PatchException("CustomiseTypeAheadResponseFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        method.addInstructions(
            returnObj,
            """
                invoke-static {p1}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->typeAheadResponse(Lcom/twitter/model/json/search/JsonTypeaheadResponse;)Lcom/twitter/model/json/search/JsonTypeaheadResponse;
            move-result-object p1
            """,
        )

        SettingsStatusLoadFingerprint.enableSettings("typeaheadCustomisation")
    }
}
