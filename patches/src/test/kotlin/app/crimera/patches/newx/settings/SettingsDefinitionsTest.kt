package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsDefinitionsTest {
    private data class FeatureSettings(
        val enabled: ToggleSettingDefinition,
        val blockedWords: TextInputSettingDefinition,
        val actions: MultiChoiceSettingDefinition,
        val clear: ActionSettingDefinition,
    )

    private val sharedDuplicateId = "newx.shared.duplicate"
    private val repeatedItem0Id = "newx.content.repeated.item0"
    private val repeatedItem1Id = "newx.content.repeated.item1"
    private val multiChoiceId = "newx.content.actions"
    private val actionId = "newx.content.action"
    private val invalidToggleId = "content.missing_namespace"

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

    @Test
    fun `toggle attaches exactly one hidden contribution dependency`() {
        val patch =
            bytecodePatch(name = "NewX settings test", default = false) {
                newXToggle(
                    id = "newx.content.test",
                    category = Categories.CONTENT,
                    strings = settingStrings("piko_newx_test"),
                    defaultValue = true,
                )
            }

        assertEquals(1, patch.dependencies.size)
        assertNull(patch.dependencies.single().name)
    }

    @Test
    fun `duplicate setting IDs fail before runtime registration`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsContributionBuilder()
                    .apply {
                        category(Categories.TIMELINE) {
                            toggle(
                                id = sharedDuplicateId,
                                titleResourceName = "piko_newx_first_title",
                                defaultValue = true,
                            )
                        }
                        category(Categories.CONTENT) {
                            toggle(
                                id = sharedDuplicateId,
                                titleResourceName = "piko_newx_second_title",
                                defaultValue = false,
                            )
                        }
                    }.build()
            }

        assertEquals(
            "Duplicate NewX setting ID: $sharedDuplicateId",
            exception.message,
        )
    }

    @Test
    fun `duplicate group IDs fail`() {
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        repeat(2) {
                            group(
                                id = "newx.content.repeated",
                                titleResourceName = "piko_newx_repeated_title",
                            ) {
                                toggle(
                                    id = if (it == 0) repeatedItem0Id else repeatedItem1Id,
                                    titleResourceName = "piko_newx_item_title",
                                    defaultValue = true,
                                )
                            }
                        }
                    }
                }.build()
        }
    }

    @Test
    fun `multi-choice defaults must reference declared options`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsContributionBuilder()
                    .apply {
                        category(Categories.CONTENT) {
                            multiChoice(
                                id = multiChoiceId,
                                titleResourceName = "piko_newx_actions_title",
                                defaultValue = setOf("missing"),
                                options =
                                    listOf(
                                        ChoiceOption("reply", "piko_newx_reply_title"),
                                    ),
                            )
                        }
                    }.build()
            }

        assertEquals(
            "Unknown default choice for $multiChoiceId: [missing]",
            exception.message,
        )
    }

    @Test
    fun `empty and malformed contributions fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder().build()
        }
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        action(
                            id = actionId,
                            titleResourceName = "piko_newx_action_title",
                            handlerClassDescriptor = "not-a-descriptor",
                        )
                    }
                }.build()
        }
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        toggle(
                            id = invalidToggleId,
                            titleResourceName = "piko_newx_action_title",
                            defaultValue = true,
                        )
                    }
                }.build()
        }
    }
}
