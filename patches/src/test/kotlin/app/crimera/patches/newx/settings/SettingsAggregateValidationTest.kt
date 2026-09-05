package app.crimera.patches.newx.settings

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsAggregateValidationTest {
    @Test
    fun `every discovered NewX contribution is valid together`() {
        val repositoryRoot = NewXValidationInputs.repositoryRoot()
        val catalogs = NewXContributionDiscovery.discover()

        assertTrue(catalogs.isNotEmpty())
        assertTrue(catalogs.any { catalog -> catalog.categories.any { it.children.isNotEmpty() } })
        SettingsAggregateValidator.validate(
            catalogs = catalogs,
            resourceNames = NewXValidationInputs.resourceNames(repositoryRoot),
            registryReads = NewXValidationInputs.registryReads(repositoryRoot),
        )
    }

    @Test
    fun `duplicate setting IDs across contributions fail`() {
        val setting = setting("newx.content.duplicate")
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs =
                        listOf(
                            catalog("newx.content", setting),
                            catalog("newx.content", setting),
                        ),
                    resourceNames = resourceNames,
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "Duplicate NewX setting ID across contributions")
    }

    @Test
    fun `repeated groups require identical metadata`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs =
                        listOf(
                            catalog("newx.content", setting("newx.content.first")),
                            catalog(
                                "newx.content",
                                setting("newx.content.second"),
                                iconResourceName = "ic_vector_other",
                            ),
                        ),
                    resourceNames = resourceNames,
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "Incompatible repeated NewX group metadata")
    }

    @Test
    fun `all node and choice resources must exist`() {
        val choiceSetting =
            setting(
                id = "newx.content.choices",
                type = AggregateSettingType.STRING_SET,
                choiceResourceNames = listOf("piko_newx_missing_choice"),
            )
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs = listOf(catalog("newx.content", choiceSetting)),
                    resourceNames = resourceNames,
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "piko_newx_missing_choice")
    }

    @Test
    fun `registry reads require a contributed ID and matching type`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs = listOf(catalog("newx.content", setting("newx.content.toggle"))),
                    resourceNames = resourceNames,
                    registryReads =
                        listOf(
                            AggregateRegistryRead(
                                "newx.content.toggle",
                                AggregateSettingType.STRING,
                                "Example.java",
                            ),
                            AggregateRegistryRead(
                                "newx.content.missing",
                                AggregateSettingType.BOOLEAN,
                                "Example.java",
                            ),
                        ),
                )
            }

        assertContains(exception.message.orEmpty(), "registry read type mismatch")
        assertContains(exception.message.orEmpty(), "Unknown NewX registry read")
    }

    private fun catalog(
        id: String,
        setting: AggregateSetting,
        titleResourceName: String = "piko_newx_group_title",
        iconResourceName: String? = "ic_vector_group",
    ) =
        AggregateCatalog(
            listOf(
                AggregateGroup(
                    id = id,
                    titleResourceName = titleResourceName,
                    summaryResourceName = null,
                    iconResourceName = iconResourceName,
                    order = 100,
                    children = listOf(AggregateSettingNode(setting)),
                ),
            ),
        )

    private fun setting(
        id: String,
        type: AggregateSettingType = AggregateSettingType.BOOLEAN,
        choiceResourceNames: List<String> = emptyList(),
    ) =
        AggregateSetting(
            id = id,
            titleResourceName = "piko_newx_setting_title",
            summaryResourceName = "piko_newx_setting_summary",
            type = type,
            choiceResourceNames = choiceResourceNames,
        )

    private val resourceNames =
        setOf(
            "piko_newx_group_title",
            "piko_newx_setting_title",
            "piko_newx_setting_summary",
        )
}
