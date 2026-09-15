package app.crimera.patches.newx.settings

import kotlin.test.Test
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
}
