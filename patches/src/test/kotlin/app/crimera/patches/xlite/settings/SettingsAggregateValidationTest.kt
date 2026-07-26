package app.crimera.patches.xlite.settings

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsAggregateValidationTest {
    @Test
    fun `every discovered X-Lite contribution is valid together`() {
        val repositoryRoot = XLiteValidationInputs.repositoryRoot()
        val catalogs = XLiteContributionDiscovery.discover()

        assertTrue(catalogs.isNotEmpty())
        assertTrue(catalogs.any { catalog -> catalog.categories.any { it.children.isNotEmpty() } })
        SettingsAggregateValidator.validate(
            catalogs = catalogs,
            resourceNames = XLiteValidationInputs.resourceNames(repositoryRoot),
            registryReads = XLiteValidationInputs.registryReads(repositoryRoot),
        )
    }

    @Test
    fun `duplicate setting IDs across contributions fail`() {
        val setting = setting("xlite.content.duplicate")
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs =
                        listOf(
                            catalog("xlite.content", setting),
                            catalog("xlite.content", setting),
                        ),
                    resourceNames = resourceNames,
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "Duplicate X-Lite setting ID across contributions")
    }

    @Test
    fun `repeated groups require identical metadata`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs =
                        listOf(
                            catalog("xlite.content", setting("xlite.content.first")),
                            catalog(
                                "xlite.content",
                                setting("xlite.content.second"),
                                titleResourceName = "piko_xlite_other_title",
                            ),
                        ),
                    resourceNames = resourceNames + "piko_xlite_other_title",
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "Incompatible repeated X-Lite group metadata")
    }

    @Test
    fun `all node and choice resources must exist`() {
        val choiceSetting =
            setting(
                id = "xlite.content.choices",
                type = AggregateSettingType.STRING_SET,
                choiceResourceNames = listOf("piko_xlite_missing_choice"),
            )
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs = listOf(catalog("xlite.content", choiceSetting)),
                    resourceNames = resourceNames,
                    registryReads = emptyList(),
                )
            }

        assertContains(exception.message.orEmpty(), "piko_xlite_missing_choice")
    }

    @Test
    fun `registry reads require a contributed ID and matching type`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                SettingsAggregateValidator.validate(
                    catalogs = listOf(catalog("xlite.content", setting("xlite.content.toggle"))),
                    resourceNames = resourceNames,
                    registryReads =
                        listOf(
                            AggregateRegistryRead(
                                "xlite.content.toggle",
                                AggregateSettingType.STRING,
                                "Example.java",
                            ),
                            AggregateRegistryRead(
                                "xlite.content.missing",
                                AggregateSettingType.BOOLEAN,
                                "Example.java",
                            ),
                        ),
                )
            }

        assertContains(exception.message.orEmpty(), "registry read type mismatch")
        assertContains(exception.message.orEmpty(), "Unknown X-Lite registry read")
    }

    private fun catalog(
        id: String,
        setting: AggregateSetting,
        titleResourceName: String = "piko_xlite_group_title",
    ) =
        AggregateCatalog(
            listOf(
                AggregateGroup(
                    id = id,
                    titleResourceName = titleResourceName,
                    summaryResourceName = null,
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
            titleResourceName = "piko_xlite_setting_title",
            summaryResourceName = "piko_xlite_setting_summary",
            type = type,
            choiceResourceNames = choiceResourceNames,
        )

    private val resourceNames =
        setOf(
            "piko_xlite_group_title",
            "piko_xlite_setting_title",
            "piko_xlite_setting_summary",
        )
}
