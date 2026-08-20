package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.bytecodePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingsDefinitionsTest {
    private val secondId = "newx.content.second"
    private val firstId = "newx.content.first"
    private val timelineItemId = "newx.timeline.item"
    private val sharedDuplicateId = "newx.shared.duplicate"
    private val repeatedItem0Id = "newx.content.repeated.item0"
    private val repeatedItem1Id = "newx.content.repeated.item1"
    private val multiChoiceId = "newx.content.actions"
    private val actionId = "newx.content.action"
    private val customScreenId = "newx.content.custom_screen"
    private val filterWordsId = "newx.content.filters.words"
    private val filterClearId = "newx.content.filters.clear"
    private val invalidToggleId = "content.missing_namespace"

    @Test
    fun `inline actions and reply sorting use distinct APK icons`() {
        assertEquals("ic_vector_more", Groups.INLINE_ACTIONS.iconResourceName)
        assertEquals("ic_vector_sort_arrows", Groups.REPLY_SORTING.iconResourceName)
    }

    @Test
    fun `catalog and children are sorted by order then id`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        toggle(
                            id = secondId,
                            titleResourceName = "piko_newx_second_title",
                            order = 20,
                            defaultValue = false,
                        )
                        toggle(
                            id = firstId,
                            titleResourceName = "piko_newx_first_title",
                            order = 10,
                            defaultValue = true,
                        )
                    }
                    category(Categories.TIMELINE) {
                        toggle(
                            id = timelineItemId,
                            titleResourceName = "piko_newx_timeline_item_title",
                            defaultValue = true,
                        )
                    }
                }.build()

        assertEquals(
            listOf("newx.timeline", "newx.content"),
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
                        titleResourceName = "piko_newx_first_title",
                        defaultValue = true,
                    )
                val input =
                    input(
                        id = filterWordsId,
                        titleResourceName = "piko_newx_filter_words_title",
                        defaultValue = "",
                    )
                val multiChoice =
                    multiChoice(
                        id = multiChoiceId,
                        titleResourceName = "piko_newx_actions_title",
                        defaultValue = emptySet(),
                        options = listOf(ChoiceOption("reply", "piko_newx_reply_title")),
                    )
                val action =
                    action(
                        id = actionId,
                        titleResourceName = "piko_newx_action_title",
                        handlerClassDescriptor =
                            "Lapp/morphe/extension/newx/settings/ClearFiltersAction;",
                    )
                val customScreen =
                    customScreen(
                        id = customScreenId,
                        titleResourceName = "piko_newx_custom_screen_title",
                        iconResourceName = "ic_vector_filter",
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/newx/settings/CustomFragment;",
                    )
                assertEquals("ic_vector_filter", customScreen.iconResourceName)
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
                    id = "newx.content.filters",
                    titleResourceName = "piko_newx_filters_title",
                ) {
                    toggle(
                        id = firstId,
                        titleResourceName = "piko_newx_first_title",
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
                "piko_newx_archive_title",
                "piko_newx_archive_summary",
            ),
            settingStrings("piko_newx_archive"),
        )
        assertEquals(
            SettingStrings("piko_newx_archive_title", null),
            settingStrings("piko_newx_archive", summary = false),
        )
        assertEquals(
            SettingStrings("piko_newx_custom_title", "piko_newx_custom_summary"),
            settingStrings("piko_newx_custom_title", "piko_newx_custom_summary"),
        )
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
    fun `nested groups preserve semantic structure`() {
        val catalog =
            SettingsContributionBuilder()
                .apply {
                    category(Categories.CONTENT) {
                        group(
                            id = "newx.content.filters",
                            titleResourceName = "piko_newx_filters_title",
                            summaryResourceName = "piko_newx_filters_summary",
                            iconResourceName = "ic_vector_filter",
                        ) {
                            input(
                                id = filterWordsId,
                                titleResourceName = "piko_newx_filter_words_title",
                                order = 10,
                                defaultValue = "",
                                inputKind = InputKind.MULTILINE,
                            )
                            action(
                                id = filterClearId,
                                titleResourceName = "piko_newx_filter_clear_title",
                                order = 20,
                                handlerClassDescriptor =
                                    "Lapp/morphe/extension/newx/settings/ClearFiltersAction;",
                            )
                        }
                    }
                }.build()

        val group = assertIs<SettingsGroupDefinition>(catalog.categories.single().children.single())
        assertEquals("newx.content.filters", group.id)
        assertEquals("piko_newx_filters_summary", group.summaryResourceName)
        assertEquals("ic_vector_filter", group.iconResourceName)
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
