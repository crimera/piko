package app.crimera.patches.xlite.settings

import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingsDefinitionsTest {
    private val secondId = "xlite.content.second"
    private val firstId = "xlite.content.first"
    private val timelineItemId = "xlite.timeline.item"
    private val sharedDuplicateId = "xlite.shared.duplicate"
    private val repeatedItem0Id = "xlite.content.repeated.item0"
    private val repeatedItem1Id = "xlite.content.repeated.item1"
    private val multiChoiceId = "xlite.content.actions"
    private val actionId = "xlite.content.action"
    private val customScreenId = "xlite.content.custom_screen"
    private val filterWordsId = "xlite.content.filters.words"
    private val filterClearId = "xlite.content.filters.clear"
    private val invalidToggleId = "content.missing_namespace"

    @Test
    fun `catalog and children are sorted by order then id`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        toggle(
                            id = secondId,
                            titleResourceName = "piko_xlite_second_title",
                            order = 20,
                            defaultValue = false,
                        )
                        toggle(
                            id = firstId,
                            titleResourceName = "piko_xlite_first_title",
                            order = 10,
                            defaultValue = true,
                        )
                    }
                    category(Categories.TIMELINE) {
                        toggle(
                            id = timelineItemId,
                            titleResourceName = "piko_xlite_timeline_item_title",
                            defaultValue = true,
                        )
                    }
                }.build()

        assertEquals(
            listOf("xlite.timeline", "xlite.content"),
            catalog.categories.map(SettingsGroupDefinition::id),
        )
        assertEquals(
            listOf(firstId, secondId),
            catalog.categories[1].children.map(SettingsNodeDefinition::id),
        )
    }

    @Test
    fun `builder methods return typed definitions`() {
        val result =
            SettingsContributionBuilder().category(Categories.CONTENT) {
                val toggle =
                    toggle(
                        id = firstId,
                        titleResourceName = "piko_xlite_first_title",
                        defaultValue = true,
                    )
                val input =
                    input(
                        id = filterWordsId,
                        titleResourceName = "piko_xlite_filter_words_title",
                        defaultValue = "",
                    )
                val multiChoice =
                    multiChoice(
                        id = multiChoiceId,
                        titleResourceName = "piko_xlite_actions_title",
                        defaultValue = emptySet(),
                        options = listOf(ChoiceOption("reply", "piko_xlite_reply_title")),
                    )
                val action =
                    action(
                        id = actionId,
                        titleResourceName = "piko_xlite_action_title",
                        handlerClassDescriptor =
                            "Lapp/morphe/extension/xlite/settings/ClearFiltersAction;",
                    )
                val customScreen =
                    customScreen(
                        id = customScreenId,
                        titleResourceName = "piko_xlite_custom_screen_title",
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/xlite/settings/CustomFragment;",
                    )
                listOf(toggle, input, multiChoice, action, customScreen)
            }

        assertIs<ToggleSettingDefinition>(result[0])
        assertIs<TextInputSettingDefinition>(result[1])
        assertIs<MultiChoiceSettingDefinition>(result[2])
        assertIs<ActionSettingDefinition>(result[3])
        assertIs<CustomScreenSettingDefinition>(result[4])
    }

    @Test
    fun `generic category and group return block results`() {
        val result =
            SettingsContributionBuilder().category(Categories.CONTENT) {
                group(
                    id = "xlite.content.filters",
                    titleResourceName = "piko_xlite_filters_title",
                ) {
                    toggle(
                        id = firstId,
                        titleResourceName = "piko_xlite_first_title",
                        defaultValue = true,
                    )
                }
            }

        assertIs<ToggleSettingDefinition>(result)
    }

    @Test
    fun `setting strings derive conventional resources`() {
        assertEquals(
            SettingStrings(
                "piko_xlite_archive_title",
                "piko_xlite_archive_summary",
            ),
            settingStrings("piko_xlite_archive"),
        )
        assertEquals(
            SettingStrings("piko_xlite_archive_title", null),
            settingStrings("piko_xlite_archive", summary = false),
        )
        assertEquals(
            SettingStrings("piko_xlite_custom_title", "piko_xlite_custom_summary"),
            settingStrings("piko_xlite_custom_title", "piko_xlite_custom_summary"),
        )
    }

    @Test
    fun `toggle attaches exactly one hidden contribution dependency`() {
        val patch =
            bytecodePatch(name = "X-Lite settings test", default = false) {
                xLiteToggle(
                    id = "xlite.content.test",
                    category = Categories.CONTENT,
                    strings = settingStrings("piko_xlite_test"),
                    defaultValue = true,
                )
            }

        assertEquals(1, patch.dependencies.size)
        assertNull(patch.dependencies.single().name)
    }

    @Test
    fun `nested groups preserve semantic structure`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        group(
                            id = "xlite.content.filters",
                            titleResourceName = "piko_xlite_filters_title",
                        ) {
                            input(
                                id = filterWordsId,
                                titleResourceName = "piko_xlite_filter_words_title",
                                order = 10,
                                defaultValue = "",
                                inputKind = InputKind.MULTILINE,
                            )
                            action(
                                id = filterClearId,
                                titleResourceName = "piko_xlite_filter_clear_title",
                                order = 20,
                                handlerClassDescriptor =
                                    "Lapp/morphe/extension/xlite/settings/ClearFiltersAction;",
                            )
                        }
                    }
                }.build()

        val group = assertIs<SettingsGroupDefinition>(catalog.categories.single().children.single())
        assertEquals("xlite.content.filters", group.id)
        assertIs<TextInputSettingDefinition>(group.children[0])
        assertIs<ActionSettingDefinition>(group.children[1])
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
                                titleResourceName = "piko_xlite_first_title",
                                defaultValue = true,
                            )
                        }
                        category(Categories.CONTENT) {
                            toggle(
                                id = sharedDuplicateId,
                                titleResourceName = "piko_xlite_second_title",
                                defaultValue = false,
                            )
                        }
                    }.build()
            }

        assertEquals(
            "Duplicate X-Lite setting ID: $sharedDuplicateId",
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
                                id = "xlite.content.repeated",
                                titleResourceName = "piko_xlite_repeated_title",
                            ) {
                                toggle(
                                    id = if (it == 0) repeatedItem0Id else repeatedItem1Id,
                                    titleResourceName = "piko_xlite_item_title",
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
                                titleResourceName = "piko_xlite_actions_title",
                                defaultValue = setOf("missing"),
                                options =
                                    listOf(
                                        ChoiceOption("reply", "piko_xlite_reply_title"),
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
                            titleResourceName = "piko_xlite_action_title",
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
                            titleResourceName = "piko_xlite_action_title",
                            defaultValue = true,
                        )
                    }
                }.build()
        }
    }
}
