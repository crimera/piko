package app.crimera.patches.newx.settings

import app.crimera.patches.downloader.downloaderManifestPatch
import app.crimera.patches.newx.misc.bringbacktwitter.bringBackTwitterPatch
import app.crimera.patches.newx.misc.clone.clonePatch
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsAggregateValidationTest {
    @Test
    fun `selectable patches cannot pull settings or added components into a mount install`() {
        val standardOnly = setOf(
            newXSettingsPatch,
            newXSettingsResourcePatch,
            downloaderManifestPatch,
            clonePatch,
            bringBackTwitterPatch,
        )
        fun Patch<*>.requiresStandardInstall(visited: MutableSet<Patch<*>> = mutableSetOf()): Boolean =
            visited.add(this) && (this in standardOnly || dependencies.any { it.requiresStandardInstall(visited) })

        val patches = NewXContributionDiscovery.discoverSelectablePatches()
        assertTrue(patches.isNotEmpty())
        assertTrue(patches.any { it.requiresStandardInstall() })
        assertTrue(patches.any { !it.requiresStandardInstall() })
        patches.forEach { patch ->
            val normal = if (patch.default) PatchAvailability.ENABLED else PatchAvailability.DISABLED
            InstallerType.entries.forEach { installer ->
                val expected =
                    if (installer == InstallerType.MOUNT && patch.requiresStandardInstall()) {
                        PatchAvailability.UNAVAILABLE
                    } else {
                        normal
                    }
                ApkArchitecture.entries.forEach { arch ->
                    assertEquals(
                        expected,
                        patch.availability?.resolve(installer, arch) ?: normal,
                        "${patch.name}: $installer / $arch",
                    )
                }
            }
        }
    }

    @Test
    fun `every discovered NewX contribution is valid together`() {
        val repositoryRoot = NewXValidationInputs.repositoryRoot()
        val catalogs =
            NewXContributionDiscovery.discover() +
                NewXValidationInputs.builtInCatalog(repositoryRoot)

        assertTrue(catalogs.isNotEmpty())
        assertTrue(catalogs.any { catalog -> catalog.categories.any { it.children.isNotEmpty() } })
        SettingsAggregateValidator.validate(
            catalogs = catalogs,
            resourceNames = NewXValidationInputs.resourceNames(repositoryRoot),
            registryReads = NewXValidationInputs.registryReads(repositoryRoot),
        )
    }
}
