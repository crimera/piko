package app.morphe.extension.newx.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DynamicColorPaletteTest {
    private static final long ORIGINAL_SURFACE = 0xFF03030300000000L;
    private static final long DIM_SURFACE = 0xFF15202B00000000L;
    private static final long BLACK_SURFACE = 0xFF00000000000000L;

    @Test
    public void standardThemeKeepsTheOriginalSurface() {
        assertEquals(ORIGINAL_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.STANDARD,
                true,
                ORIGINAL_SURFACE
        ));
    }

    @Test
    public void lightsOutAmoledUsesBlackChrome() {
        assertEquals(BLACK_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                true,
                ORIGINAL_SURFACE
        ));
    }

    /*
     * Guards issue #80: the app's own "Dim" appearance resolves the dark palette for some
     * surfaces, so chrome and popups stayed blue-grey while the rest of the app was already pure
     * black. The chosen style owns that family now, in either direction.
     */
    @Test
    public void dimAppearanceFollowsTheSelectedDarkStyle() {
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                false,
                ORIGINAL_SURFACE
        ));
        assertEquals(BLACK_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                true,
                ORIGINAL_SURFACE
        ));
    }

    @Test
    public void dimStyleKeepsTheClassicDimChrome() {
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                false,
                ORIGINAL_SURFACE
        ));
    }

    /*
     * Pins the palette contract of both styles. AMOLED must keep the app's lights-out elevated
     * surfaces instead of painting popups, dialogs, and glass panels pure black on a black base,
     * and must never fall back to the dim blue-grey family.
     */
    @Test
    public void amoledStyleOwnsPureBlackBaseSurfacesAndLightsOutElevation() {
        assertEquals(
                0xFF00000000000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.CELL_BACKGROUND, true)
        );
        assertEquals(
                0xFF00000000000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.APP_BACKGROUND, true)
        );
        assertEquals(
                0x8000000000000000L,
                DynamicColorPalette.darkStyleColor(
                        DynamicColorPalette.CELL_BACKGROUND_TRANSLUCENT,
                        true
                )
        );
        assertEquals(
                0xFF12131400000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.HIGHLIGHT_BACKGROUND, true)
        );
        assertEquals(
                0xCC24242400000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.GLASS_BACKGROUND, true)
        );
    }

    @Test
    public void dimStyleRestoresTheClassicDimFamily() {
        assertEquals(
                0xFF15202B00000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.CELL_BACKGROUND, false)
        );
        assertEquals(
                0xFF15202B00000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.APP_BACKGROUND, false)
        );
        assertEquals(
                0xBF15202B00000000L,
                DynamicColorPalette.darkStyleColor(
                        DynamicColorPalette.CELL_BACKGROUND_TRANSLUCENT,
                        false
                )
        );
        assertEquals(
                0xFF10192200000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.HIGHLIGHT_BACKGROUND, false)
        );
        assertEquals(
                0xCC15202B00000000L,
                DynamicColorPalette.darkStyleColor(DynamicColorPalette.GLASS_BACKGROUND, false)
        );
    }
}
