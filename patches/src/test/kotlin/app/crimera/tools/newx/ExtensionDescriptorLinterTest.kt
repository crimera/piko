package app.crimera.tools.newx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resolution coverage for the extension descriptor linter. A silently skipped reference makes
 * the gate look green while a `NoSuchMethodError` ships, and a wrongly resolved constant turns
 * the gate into a false alarm, so each test pins one rule the real sources depend on.
 */
class ExtensionDescriptorLinterTest {
    @Test
    fun `resolves a multi-level constant chain`() {
        val file =
            sourceFile(
                "app/crimera/patches/newx/utils/Constants.kt",
                """
                const val EXTENSION_PACKAGE = "Lapp/morphe/extension/newx"
                const val SETTINGS_PACKAGE = "${'$'}EXTENSION_PACKAGE/settings"
                const val SETTINGS_REGISTRY_DESCRIPTOR = "${'$'}SETTINGS_PACKAGE/SettingsRegistry;"
                """,
            )

        val (references, skipped) =
            findDescriptorReferences(
                file.path,
                """const-string v0, "${'$'}SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;Z)Z"""",
                collectConstantScopes(listOf(file)).forFile(file.path),
            )

        assertEquals(1, references.size)
        assertEquals("Lapp/morphe/extension/newx/settings/SettingsRegistry;", references.single().owner)
        assertEquals("getBooleanOrDefault(Ljava/lang/String;Z)Z", references.single().member)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun `resolves constants imported from another file`() {
        val constants =
            sourceFile(
                "app/crimera/patches/twitter/utils/Constants.kt",
                """
                package app.crimera.patches.twitter.utils

                object Constants {
                    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/twitter"
                    const val PATCHES_DESCRIPTOR = "${'$'}INTEGRATIONS_PACKAGE/patches"
                }
                """,
            )
        val patch =
            sourceFile(
                "app/crimera/patches/twitter/ads/TimelineEntryHookPatch.kt",
                """
                package app.crimera.patches.twitter.ads

                import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR

                val hook = "${'$'}PATCHES_DESCRIPTOR/TimelineEntry;->checkEntry(Ljava/lang/Object;)Ljava/lang/Object;"
                """,
            )
        val scopes = collectConstantScopes(listOf(constants, patch))

        val (references, skipped) = findDescriptorReferences(patch.path, patch.text, scopes.forFile(patch.path))

        assertEquals(1, references.size)
        assertEquals("Lapp/morphe/extension/twitter/patches/TimelineEntry;", references.single().owner)
        assertEquals("checkEntry(Ljava/lang/Object;)Ljava/lang/Object;", references.single().member)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun `a same-named constant with different values is skipped instead of misresolved`() {
        val instagram =
            sourceFile(
                "app/crimera/patches/instagram/utils/Constants.kt",
                """
                object Constants {
                    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/instagram"
                    const val PATCHES_DESCRIPTOR = "${'$'}INTEGRATIONS_PACKAGE/patches"
                }
                """,
            )
        val twitter =
            sourceFile(
                "app/crimera/patches/twitter/utils/Constants.kt",
                """
                object Constants {
                    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/twitter"
                    const val PATCHES_DESCRIPTOR = "${'$'}INTEGRATIONS_PACKAGE/patches"
                }
                """,
            )
        // No import: the name is ambiguous here, so the reference must not be resolved against
        // whichever file happened to be scanned first.
        val patch =
            sourceFile(
                "app/crimera/patches/twitter/ads/TimelineEntryHookPatch.kt",
                """val hook = "${'$'}PATCHES_DESCRIPTOR/TimelineEntry;->checkEntry()V"""",
            )
        val scopes = collectConstantScopes(listOf(instagram, twitter, patch))

        val (references, skipped) = findDescriptorReferences(patch.path, patch.text, scopes.forFile(patch.path))

        assertTrue(references.isEmpty())
        assertEquals(1, skipped.size)
    }

    @Test
    fun `identical literal constants are usable from any file`() {
        val first = sourceFile("app/a/Fingerprint.kt", """const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"""")
        val second = sourceFile("app/b/Fingerprint.kt", """const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"""")
        val patch =
            sourceFile(
                "app/c/Patch.kt",
                "Lapp/morphe/extension/newx/misc/NavBarReplacement;->overrideIcon(" +
                    "${'$'}OBJECT_DESCRIPTOR${'$'}OBJECT_DESCRIPTOR)${'$'}OBJECT_DESCRIPTOR",
            )
        val scopes = collectConstantScopes(listOf(first, second, patch))

        val (references, skipped) = findDescriptorReferences(patch.path, patch.text, scopes.forFile(patch.path))

        assertEquals(1, references.size)
        assertEquals("overrideIcon(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", references.single().member)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun `resolves a literal owner and the escaped dollar idiom`() {
        val (references, skipped) =
            findDescriptorReferences(
                "fixture.kt",
                "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getLegacyToggleListener()" +
                    "Landroid/widget/CompoundButton\${'\$'}OnCheckedChangeListener;",
                ConstantScope.EMPTY,
            )

        assertEquals(1, references.size)
        assertEquals(
            "getLegacyToggleListener()Landroid/widget/CompoundButton\$OnCheckedChangeListener;",
            references.single().member,
        )
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun `reports a dynamic member name instead of dropping it`() {
        // The owner is only recognisable after resolving its constant, so the marker never
        // appears in the raw text. Dropping such a match would silently reduce coverage.
        val constants =
            collectConstantScopes(
                listOf(sourceFile("Constants.kt", """const val NAV_BAR_REPLACEMENT_DESCRIPTOR = "Lapp/morphe/extension/newx/misc/NavBarReplacement;"""")),
            )

        val (references, skipped) =
            findDescriptorReferences(
                "fixture.kt",
                "${'$'}NAV_BAR_REPLACEMENT_DESCRIPTOR->\$bridgeName(Ljava/lang/Object;)V",
                constants.forFile("Constants.kt"),
            )

        assertTrue(references.isEmpty())
        assertEquals(1, skipped.size)
    }

    @Test
    fun `ignores members that are not extension descriptors`() {
        val (references, skipped) =
            findDescriptorReferences(
                "fixture.kt",
                """invoke-static {v0}, Lapp/twitter/Foo;->bar(Ljava/lang/String;)V""",
                ConstantScope.EMPTY,
            )

        assertTrue(references.isEmpty())
        assertTrue(skipped.isEmpty())
    }

    private fun sourceFile(
        path: String,
        text: String,
    ) = SourceFile(path, text)
}
