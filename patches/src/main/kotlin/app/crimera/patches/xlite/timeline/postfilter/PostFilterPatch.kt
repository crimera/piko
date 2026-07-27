package app.crimera.patches.xlite.timeline.postfilter

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.InputKind
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.input
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.timeline.XLiteTimelineSuccessFingerprint
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val postFilterPatch =
    bytecodePatch(
        name = "X-Lite: Filter posts by keyword",
        description = "Filters X-Lite posts using user-defined words and phrases.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val enabled =
            xLiteSettings {
                category(Categories.CONTENT) {
                    group(
                        id = "xlite.content.post_filtering",
                        strings = settingStrings("piko_xlite_post_filtering"),
                        order = 300,
                    ) {
                        val enabled =
                            toggle(
                                id = "xlite.content.post_filtering.enabled",
                                strings = settingStrings("piko_xlite_post_filtering_enabled"),
                                order = 100,
                                defaultValue = true,
                            )
                        input(
                            id = "xlite.content.post_filtering.blocked_words",
                            strings = settingStrings("piko_xlite_post_filtering_blocked_words"),
                            order = 200,
                            defaultValue = "",
                            inputKind = InputKind.MULTILINE,
                        )
                        enabled
                    }
                }
            }

        execute {
            val matches = XLiteTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val enabledRead =
                    enabled.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    enabledRead.nextIndex,
                    """
                        invoke-static {p2, v${enabledRead.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterPostsByKeyword(Ljava/lang/Object;Z)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }
