package app.crimera.patches.xlite.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SettingsDefinitionsTest {
    @Test
    fun `catalog and children are sorted by order then id`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(XLiteSettingsCategory.CONTENT) {
                        toggle(
                            id = "xlite.content.second",
                            titleResourceName = "piko_xlite_second_title",
                            order = 20,
                            defaultValue = false,
                        )
                        toggle(
                            id = "xlite.content.first",
                            titleResourceName = "piko_xlite_first_title",
                            order = 10,
                            defaultValue = true,
                        )
                    }
                    category(XLiteSettingsCategory.TIMELINE) {
                        toggle(
                            id = "xlite.timeline.item",
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
            listOf("xlite.content.first", "xlite.content.second"),
            catalog.categories[1].children.map(SettingsNodeDefinition::id),
        )
    }

    @Test
    fun `nested groups preserve semantic structure`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(XLiteSettingsCategory.CONTENT) {
                        group(
                            id = "xlite.content.filters",
                            titleResourceName = "piko_xlite_filters_title",
                        ) {
                            input(
                                id = "xlite.content.filters.words",
                                titleResourceName = "piko_xlite_filter_words_title",
                                order = 10,
                                defaultValue = "",
                                inputKind = InputKind.MULTILINE,
                            )
                            action(
                                id = "xlite.content.filters.clear",
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
                        category(XLiteSettingsCategory.TIMELINE) {
                            toggle(
                                id = "xlite.shared.duplicate",
                                titleResourceName = "piko_xlite_first_title",
                                defaultValue = true,
                            )
                        }
                        category(XLiteSettingsCategory.CONTENT) {
                            toggle(
                                id = "xlite.shared.duplicate",
                                titleResourceName = "piko_xlite_second_title",
                                defaultValue = false,
                            )
                        }
                    }.build()
            }

        assertEquals(
            "Duplicate X-Lite setting ID: xlite.shared.duplicate",
            exception.message,
        )
    }

    @Test
    fun `duplicate group IDs fail`() {
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder()
                .apply {
                    category(XLiteSettingsCategory.CONTENT) {
                        repeat(2) {
                            group(
                                id = "xlite.content.repeated",
                                titleResourceName = "piko_xlite_repeated_title",
                            ) {
                                toggle(
                                    id = "xlite.content.repeated.item$it",
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
                        category(XLiteSettingsCategory.CONTENT) {
                            multiChoice(
                                id = "xlite.content.actions",
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
            "Unknown default choice for xlite.content.actions: [missing]",
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
                    category(XLiteSettingsCategory.CONTENT) {
                        action(
                            id = "xlite.content.action",
                            titleResourceName = "piko_xlite_action_title",
                            handlerClassDescriptor = "not-a-descriptor",
                        )
                    }
                }.build()
        }
        assertFailsWith<IllegalArgumentException> {
            SettingsContributionBuilder()
                .apply {
                    category(XLiteSettingsCategory.CONTENT) {
                        toggle(
                            id = "content.missing_namespace",
                            titleResourceName = "piko_xlite_action_title",
                            defaultValue = true,
                        )
                    }
                }.build()
        }
    }
}
