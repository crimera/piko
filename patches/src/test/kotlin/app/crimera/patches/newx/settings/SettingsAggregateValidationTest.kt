package app.crimera.patches.newx.settings

import app.crimera.patches.downloader.downloaderManifestPatch
import app.crimera.patches.newx.misc.bringbacktwitter.bringBackTwitterPatch
import app.crimera.patches.newx.misc.clone.clonePatch
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchAvailability
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsAggregateValidationTest {
    @Test
    fun `packaged settings static calls resolve against the packaged Morphe runtime`() {
        val dex = requireNotNull(javaClass.getResourceAsStream("/extensions/shared.mpe")).buffered().use {
            DexBackedDexFile.fromInputStream(Opcodes.getDefault(), it)
        }
        val classes = dex.classes.associateBy { it.type }
        fun resolves(reference: MethodReference, owner: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
            if (!visited.add(owner)) return false
            val definition = classes[owner] ?: return false
            if (definition.methods.any { method ->
                    method.name == reference.name && method.returnType == reference.returnType &&
                        method.parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)
                }
            ) return true
            return (listOfNotNull(definition.superclass) + definition.interfaces).any { resolves(reference, it, visited) }
        }
        val references = dex.classes.filter { it.type.startsWith("Lapp/morphe/extension/crimera/settings/") }
            .flatMap { it.methods }.flatMap { it.implementation?.instructions?.toList().orEmpty() }
            .filter { it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE }
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            .filter { it.definingClass.startsWith("Lapp/morphe/extension/shared/") }
            .distinctBy(MethodReference::toString)
        assertTrue(references.isNotEmpty(), "No packaged settings/runtime calls were checked")
        assertEquals(emptyList(), references.filterNot { resolves(it, it.definingClass) }.map(MethodReference::toString))
    }

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
