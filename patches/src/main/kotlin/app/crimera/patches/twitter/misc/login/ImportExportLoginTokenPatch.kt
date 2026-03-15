package app.crimera.patches.twitter.misc.login

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.getResourceId
import app.morphe.shared.misc.mapping.resourceMappingPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSIONS_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/logintoken/ImportExportLoginTokenPatch;"

private object OcfCtaStepDynamicLayoutInflateFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/onboarding/ocf/common/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        literal(getResourceId(ResourceType.LAYOUT, "ocf_cta_step_dynamic")),
        methodCall(definingClass = "Landroid/view/LayoutInflater;", name = "inflate"),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately())
    )
)

@Suppress("unused")
val importExportLoginTokenPatch = bytecodePatch(
    name = "Import/Export login token",
    description = "Adds an feature to export and import the token of accounts. "
    + "This is useful when logging in on your second device or when re-installing piko.",
) {
    compatibleWith("com.twitter.android")

    dependsOn(
        resourceMappingPatch,
        settingsPatch,
        resourcePatch {
            execute {
                document("res/layout/ocf_cta_step_dynamic.xml").use {
                    val newElement = it.createElement("com.twitter.ui.components.text.legacy.TypefacesTextView").apply {
                        setAttribute("android:id", "@+id/import_token_text")
                        setAttribute("android:text", "@string/piko_login_token_import_token_button_text")
                        setAttribute("android:gravity", "center_vertical")
                        setAttribute("android:layout_width", "match_parent")
                        setAttribute("android:layout_height", "wrap_content")
                        setAttribute("android:layout_marginBottom", "@dimen/ocf_standard_spacing")
                        setAttribute("android:layout_marginHorizontal", "@dimen/ocf_screen_padding_wide")
                        setAttribute("android:clickable", "true")
                        setAttribute("style", "@style/OcfBodyText")
                    }
                    it.documentElement.appendChild(newElement)
                }

                copyResources(
                    "twitter/settings",
                    ResourceGroup("layout", "fragment_export_token.xml")
                )
            }
        }
        )

    execute {
        OcfCtaStepDynamicLayoutInflateFingerprint.let {
            it.method.apply {
                val targetIndex = it.instructionMatches.last().index
                val targetRegister = getInstruction<OneRegisterInstruction>(targetIndex).registerA
                addInstructions(
                    targetIndex + 1,
                    """
                    invoke-static {v$targetRegister}, $EXTENSIONS_CLASS_DESCRIPTOR->initImportButton(Landroid/view/View;)V
                """
                )
            }
        }

        SettingsStatusLoadFingerprint.enableSettings("exportLoginToken")
    }
}
