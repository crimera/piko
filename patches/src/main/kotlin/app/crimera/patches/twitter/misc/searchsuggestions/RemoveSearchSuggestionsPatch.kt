package app.crimera.patches.twitter.misc.searchsuggestions

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private object SearchSuggestionFingerprint : Fingerprint(
    definingClass = "/search/provider/",
    returnType = "Ljava/util/Collection;",
    strings = listOf(
        "type",
        "query_id",
    )
)

@Suppress("unused")
val RemoveSearchSuggestions =
    bytecodePatch(
        name = "Remove search suggestions",
        description = "Hide/Remove search suggestion in explore section",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            SearchSuggestionFingerprint.method.apply {

                val firstInstruction = getInstruction(0)

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->RemoveSearchSuggestions()Z
                    move-result v0
                    if-eqz v0, :cond_1212
                    const v0, 0x0
                    return-object v0
                    """.trimIndent(),
                    ExternalLabel("cond_1212", firstInstruction),
                )

                SettingsStatusLoadFingerprint.enableSettings("removeSearchSuggestions")
            }
        }
    }
