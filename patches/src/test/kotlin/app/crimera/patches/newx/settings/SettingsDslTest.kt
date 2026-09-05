package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsDslTest {
    private data class FeatureSettings(
        val enabled: ToggleSettingDefinition,
        val blockedWords: TextInputSettingDefinition,
        val actions: MultiChoiceSettingDefinition,
        val clear: ActionSettingDefinition,
    )

    @Test
    fun `advanced DSL returns typed feature settings and indexes its catalog`() {
        SettingsContributionIndex.resetForTests()
        try {
            lateinit var settings: FeatureSettings
            val patch =
                bytecodePatch(name = "Advanced NewX settings test", default = false) {
                    settings =
                        newXSettings {
                            category(Categories.CONTENT) {
                                group(
                                    id = "newx.content.post_filtering",
                                    strings = settingStrings("piko_newx_post_filtering"),
                                    order = 300,
                                ) {
                                    FeatureSettings(
                                        enabled =
                                            toggle(
                                                id = "newx.content.post_filtering.enabled",
                                                strings =
                                                    settingStrings("piko_newx_post_filtering_enabled"),
                                                order = 100,
                                                defaultValue = false,
                                            ),
                                        blockedWords =
                                            input(
                                                id = "newx.content.post_filtering.blocked_words",
                                                strings =
                                                    settingStrings(
                                                        "piko_newx_post_filtering_blocked_words",
                                                    ),
                                                order = 200,
                                                defaultValue = "",
                                                inputKind = InputKind.MULTILINE,
                                            ),
                                        actions =
                                            multiChoice(
                                                id = "newx.content.post_filtering.actions",
                                                strings =
                                                    settingStrings("piko_newx_post_filtering_actions"),
                                                order = 300,
                                                defaultValue = setOf("reply"),
                                                options =
                                                    listOf(
                                                        choice(
                                                            "reply",
                                                            "piko_newx_post_filtering_reply_title",
                                                        ),
                                                    ),
                                            ),
                                        clear =
                                            action(
                                                id = "newx.content.post_filtering.clear",
                                                strings =
                                                    settingStrings(
                                                        "piko_newx_post_filtering_clear",
                                                        summary = false,
                                                    ),
                                                order = 400,
                                                handlerClassDescriptor =
                                                    "Lapp/morphe/extension/newx/content/ClearFiltersAction;",
                                            ),
                                    )
                                }
                            }
                        }
                }

            assertEquals("newx.content.post_filtering.enabled", settings.enabled.id)
            assertEquals(InputKind.MULTILINE, settings.blockedWords.inputKind)
            assertEquals(setOf("reply"), settings.actions.defaultValue)
            assertEquals("newx.content.post_filtering.clear", settings.clear.id)
            assertEquals(1, patch.dependencies.size)

            val snapshot = SettingsContributionIndex.snapshot()
            val group =
                assertIs<SettingsGroupDefinition>(
                    snapshot.single().categories.single().children.single(),
                )
            assertEquals(
                listOf(
                    "newx.content.post_filtering.enabled",
                    "newx.content.post_filtering.blocked_words",
                    "newx.content.post_filtering.actions",
                    "newx.content.post_filtering.clear",
                ),
                group.children.map(SettingsNodeDefinition::id),
            )
            assertFailsWith<UnsupportedOperationException> {
                (snapshot as MutableList).clear()
            }
            assertFailsWith<UnsupportedOperationException> {
                (group.children as MutableList).clear()
            }

            SettingsContributionIndex.resetForTests()
            assertTrue(SettingsContributionIndex.snapshot().isEmpty())
        } finally {
            SettingsContributionIndex.resetForTests()
        }
    }
}
