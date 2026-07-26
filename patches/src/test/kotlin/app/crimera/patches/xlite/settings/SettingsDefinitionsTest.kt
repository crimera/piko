package app.crimera.patches.xlite.settings

import app.morphe.extension.xlite.api.XLiteSettings.Categories
import app.morphe.extension.xlite.api.SettingKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SettingsDefinitionsTest {
    private val keySecond = SettingKey<Boolean>("xlite.content.second")
    private val keyFirst = SettingKey<Boolean>("xlite.content.first")
    private val keyTimelineItem = SettingKey<Boolean>("xlite.timeline.item")
    private val keySharedDuplicate = SettingKey<Boolean>("xlite.shared.duplicate")
    private val keyRepeatedItem0 = SettingKey<Boolean>("xlite.content.repeated.item0")
    private val keyRepeatedItem1 = SettingKey<Boolean>("xlite.content.repeated.item1")
    private val keyMultiChoice = SettingKey<Set<String>>("xlite.content.actions")
    private val keyAction = SettingKey<String>("xlite.content.action")
    private val keyFilterWords = SettingKey<String>("xlite.content.filters.words")
    private val keyFilterClear = SettingKey<String>("xlite.content.filters.clear")
    private val keyInvalidToggle = SettingKey<Boolean>("content.missing_namespace")

    @Test
    fun `catalog and children are sorted by order then id`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        toggle(
                            key = keySecond,
                            titleResourceName = "piko_xlite_second_title",
                            order = 20,
                            defaultValue = false,
                        )
                        toggle(
                            key = keyFirst,
                            titleResourceName = "piko_xlite_first_title",
                            order = 10,
                            defaultValue = true,
                        )
                    }
                    category(Categories.TIMELINE) {
                        toggle(
                            key = keyTimelineItem,
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
                    category(Categories.CONTENT) {
                        group(
                            id = "xlite.content.filters",
                            titleResourceName = "piko_xlite_filters_title",
                        ) {
                            input(
                                key = keyFilterWords,
                                titleResourceName = "piko_xlite_filter_words_title",
                                order = 10,
                                defaultValue = "",
                                inputKind = InputKind.MULTILINE,
                            )
                            action(
                                key = keyFilterClear,
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
                                key = keySharedDuplicate,
                                titleResourceName = "piko_xlite_first_title",
                                defaultValue = true,
                            )
                        }
                        category(Categories.CONTENT) {
                            toggle(
                                key = keySharedDuplicate,
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
                    category(Categories.CONTENT) {
                        repeat(2) {
                            group(
                                id = "xlite.content.repeated",
                                titleResourceName = "piko_xlite_repeated_title",
                            ) {
                                toggle(
                                    key = if (it == 0) keyRepeatedItem0 else keyRepeatedItem1,
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
                                key = keyMultiChoice,
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
                    category(Categories.CONTENT) {
                        action(
                            key = keyAction,
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
                            key = keyInvalidToggle,
                            titleResourceName = "piko_xlite_action_title",
                            defaultValue = true,
                        )
                    }
                }.build()
        }
    }
}
