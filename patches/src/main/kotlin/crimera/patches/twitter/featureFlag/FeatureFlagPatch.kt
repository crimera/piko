package crimera.patches.twitter.featureFlag

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.*


private val featureFlagResourcePatch = resourcePatch {
    execute { context ->
        context.copyResources(
            "twitter/settings",
            ResourceGroup(
                "layout",
                "feature_flags_view.xml",
                "item_row.xml",
                "search_item_row.xml",
                "search_dialog.xml"
            )
        )
    }
}

@Suppress("unused")
val featureFlagPatch = bytecodePatch(
    name = "Hook feature flag",
) {
    dependsOn(settingsPatch, featureFlagResourcePatch)
    compatibleWith("com.twitter.android")

    val result by featureFlagFingerprint()
    val integrationsUtilsFingerprintMatch by integrationsUtilsFingerprint()
    val customAdapter by customAdapterFingerprint()
    val getCountMatch by getCountFingerprint()
    val onCreateViewHolderMatch by onCreateViewHolderFingerprint()
    val onBindViewHolderMatch by onBindViewHolderFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val methods = result.mutableClass.methods
        val booleanMethod = methods.first { it.returnType == "Z" && it.parameters == listOf("Ljava/lang/String;", "Z") }

        val METHOD = """
            invoke-static {p1,v0}, ${PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
        """.trimIndent()

        val loc = booleanMethod.instructions.first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index

        booleanMethod.addInstructions(loc + 1, METHOD)

        integrationsUtilsFingerprintMatch.mutableMethod.addInstruction(
            1, "${FSTS_DESCRIPTOR}->load()V"
        )

        // Change the getCount override method name
        customAdapter.mutableMethod.name = getCountMatch.method.name

        // onCreateViewHolder
        customAdapter.mutableClass.methods.first { it.name == "onCreateViewHolder" }.name =
            onCreateViewHolderMatch.method.name

        // onBindViewHolder
        customAdapter.mutableClass.methods.first { it.name == "onBindViewHolder" }.name =
            onBindViewHolderMatch.method.name

        settingsStatusMatch.enableSettings("enableFeatureFlags")
    }
}