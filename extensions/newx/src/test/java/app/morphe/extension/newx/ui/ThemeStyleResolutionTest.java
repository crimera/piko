package app.morphe.extension.newx.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Guards the theme chooser resolution. Unregistered (patch absent), legacy AMOLED, and unknown
 * values resolve to high contrast so extension-owned screens keep the previous pure-black dark
 * surfaces instead of falling back to the dim family.
 */
public final class ThemeStyleResolutionTest {
    @Test
    public void unregisteredThemeFallsBackToHighContrast() {
        assertEquals("contrast", Theme.resolveThemeStyle(false, "contrast"));
        assertEquals("contrast", Theme.resolveThemeStyle(false, "material"));
        assertEquals("contrast", Theme.resolveThemeStyle(false, "dim"));
        assertEquals("contrast", Theme.resolveThemeStyle(false, "unexpected"));
    }

    @Test
    public void registeredThemeFollowsTheUserChoice() {
        assertEquals("default", Theme.resolveThemeStyle(true, "default"));
        assertEquals("contrast", Theme.resolveThemeStyle(true, "contrast"));
        assertEquals("material", Theme.resolveThemeStyle(true, "material"));
        assertEquals("dim", Theme.resolveThemeStyle(true, "dim"));
    }

    @Test
    public void legacyAmoledValueMigratesToHighContrast() {
        assertEquals("contrast", Theme.resolveThemeStyle(true, "amoled"));
    }

    @Test
    public void unknownStoredValueStaysOnHighContrast() {
        assertEquals("contrast", Theme.resolveThemeStyle(true, "unexpected"));
        assertEquals("contrast", Theme.resolveThemeStyle(true, ""));
    }
}
