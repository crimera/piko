package app.morphe.extension.newx.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DynamicColorPaletteTest {
    private static final long ORIGINAL_SURFACE = 0xFF03030300000000L;
    private static final long BLACK_SURFACE = 0xFF00000000000000L;
    private static final long WHITE_SURFACE = 0xFFFFFFFF00000000L;
    private static final long DIM_SURFACE = 0xFF15202B00000000L;

    @Test
    public void themeNormalizationRecognizesAllChooserOptions() {
        assertEquals("default", DynamicColorPalette.normalizeThemeStyle("default"));
        assertEquals("material", DynamicColorPalette.normalizeThemeStyle("material"));
        assertEquals("contrast", DynamicColorPalette.normalizeThemeStyle("contrast"));
        assertEquals("dim", DynamicColorPalette.normalizeThemeStyle("dim"));
    }

    @Test
    public void legacyAmoledValueMigratesToHighContrast() {
        assertEquals("contrast", DynamicColorPalette.normalizeThemeStyle("amoled"));
    }

    @Test
    public void unknownThemeFallsBackToHighContrast() {
        assertEquals("contrast", DynamicColorPalette.normalizeThemeStyle("unexpected"));
        assertEquals("contrast", DynamicColorPalette.normalizeThemeStyle(""));
        assertEquals("contrast", DynamicColorPalette.normalizeThemeStyle(null));
    }

    @Test
    public void standardThemeKeepsTheOriginalSurface() {
        assertEquals(ORIGINAL_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.STANDARD,
                "contrast",
                ORIGINAL_SURFACE
        ));
    }

    @Test
    public void highContrastChromeIsPureBlack() {
        assertEquals(BLACK_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                "contrast",
                ORIGINAL_SURFACE
        ));
        assertEquals(BLACK_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                "contrast",
                ORIGINAL_SURFACE
        ));
    }

    @Test
    public void dimThemeKeepsTheClassicDimChrome() {
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                "dim",
                ORIGINAL_SURFACE
        ));
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                "dim",
                ORIGINAL_SURFACE
        ));
    }

    /*
     * Pins the high-contrast background contract: base surfaces are pure black/white with
     * kept elevation, so panels stay visible against the base while accents stay dynamic.
     */
    @Test
    public void highContrastDarkBackgroundsArePureBlack() {
        assertEquals(
                BLACK_SURFACE,
                DynamicColorPalette.contrastDarkColor(DynamicColorPalette.CELL_BACKGROUND)
        );
        assertEquals(
                BLACK_SURFACE,
                DynamicColorPalette.contrastDarkColor(DynamicColorPalette.APP_BACKGROUND)
        );
        assertEquals(
                0x8000000000000000L,
                DynamicColorPalette.contrastDarkColor(
                        DynamicColorPalette.CELL_BACKGROUND_TRANSLUCENT
                )
        );
        assertEquals(
                0xFF12131400000000L,
                DynamicColorPalette.contrastDarkColor(DynamicColorPalette.HIGHLIGHT_BACKGROUND)
        );
        assertEquals(
                0xCC24242400000000L,
                DynamicColorPalette.contrastDarkColor(DynamicColorPalette.GLASS_BACKGROUND)
        );
    }

    @Test
    public void highContrastLightBackgroundsArePureWhite() {
        assertEquals(
                WHITE_SURFACE,
                DynamicColorPalette.contrastLightColor(DynamicColorPalette.CELL_BACKGROUND)
        );
        assertEquals(
                WHITE_SURFACE,
                DynamicColorPalette.contrastLightColor(DynamicColorPalette.APP_BACKGROUND)
        );
        assertEquals(
                0x80FFFFFF00000000L,
                DynamicColorPalette.contrastLightColor(
                        DynamicColorPalette.CELL_BACKGROUND_TRANSLUCENT
                )
        );
        assertEquals(
                0xFFEEEEEE00000000L,
                DynamicColorPalette.contrastLightColor(DynamicColorPalette.HIGHLIGHT_BACKGROUND)
        );
        assertEquals(
                0xCCEEEEEE00000000L,
                DynamicColorPalette.contrastLightColor(DynamicColorPalette.GLASS_BACKGROUND)
        );
    }
}
