package app.crimera.patches.twitter.misc.searchsuggestions

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private object searchDbInsertFingerprint : Fingerprint(
    strings = listOf(
        "search_queries",
        "findSearchQuery: ",
        "LOWER(query)=LOWER(?) AND LOWER(name)=LOWER(?) AND type=? AND latitude=? AND longitude=?",
    )
)

@Suppress("unused")
val pauseSearchSuggestion =
    bytecodePatch(
        name = "Pause search suggestions",
        description = "Search suggestions will not be saved locally",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            searchDbInsertFingerprint.method.apply {

                val firstInstruction = getInstruction(0)

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->pauseSearchSuggestions()Z
                    move-result v0
                    if-eqz v0, :cond_1212
                    return-void
                    """.trimIndent(),
                    ExternalLabel("cond_1212", firstInstruction),
                )

                SettingsStatusLoadFingerprint.enableSettings("pauseSearchSuggestions")
            }
        }
    }
