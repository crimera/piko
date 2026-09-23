package app.morphe.extension.newx.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DynamicColorPaletteTest {
    private static final long ORIGINAL_SURFACE = 0xFF03030300000000L;
    private static final long DIM_SURFACE = 0xFF15202B00000000L;
    private static final long DYNAMIC_SURFACE = 0xFF1B1B1F00000000L;
    private static final long BLACK_SURFACE = 0xFF00000000000000L;

    @Test
    public void standardThemeKeepsTheOriginalSurface() {
        assertEquals(ORIGINAL_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.STANDARD,
                true,
                true,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
    }

    @Test
    public void dimThemeUsesTheDynamicSurfaceWhenEnabled() {
        assertEquals(DYNAMIC_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                true,
                false,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
    }

    @Test
    public void dimThemeRestoresBlueEvenWhenAmoledIsEnabled() {
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.DIM,
                false,
                true,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
    }

    @Test
    public void lightsOutAmoledUsesBlackBeforeDynamicSurface() {
        assertEquals(BLACK_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                true,
                true,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
    }

    @Test
    public void lightsOutUsesDynamicOrDimSurfaceWithoutAmoled() {
        assertEquals(DYNAMIC_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                true,
                false,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
        assertEquals(DIM_SURFACE, DynamicColorPalette.resolveXdsChromeBackground(
                TwitterTheme.LIGHTS_OUT,
                false,
                false,
                ORIGINAL_SURFACE,
                DYNAMIC_SURFACE
        ));
    }
}
