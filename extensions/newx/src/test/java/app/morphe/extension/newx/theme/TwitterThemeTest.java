package app.morphe.extension.newx.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TwitterThemeTest {
    @Test
    public void forcedDarkUsesLightsOutAppearance() {
        assertEquals(
                TwitterTheme.LIGHTS_OUT,
                TwitterTheme.resolve("1", "lights_out", false)
        );
    }

    @Test
    public void forcedDarkUsesDimAppearance() {
        assertEquals(
                TwitterTheme.DIM,
                TwitterTheme.resolve("1", "dim", false)
        );
    }

    @Test
    public void systemModeUsesSystemNightState() {
        assertEquals(
                TwitterTheme.LIGHTS_OUT,
                TwitterTheme.resolve("2", "lights_out", true)
        );
        assertEquals(
                TwitterTheme.STANDARD,
                TwitterTheme.resolve("2", "lights_out", false)
        );
    }

    @Test
    public void forcedLightIgnoresSystemNightState() {
        assertEquals(
                TwitterTheme.STANDARD,
                TwitterTheme.resolve("0", "lights_out", true)
        );
    }

    @Test
    public void unknownAppearanceFallsBackToStandard() {
        assertEquals(
                TwitterTheme.STANDARD,
                TwitterTheme.resolve("1", "unknown", true)
        );
    }

    @Test
    public void knownAccentValuesResolve() {
        assertEquals(TwitterTheme.Accent.BLUE, TwitterTheme.resolveAccent("blue"));
        assertEquals(TwitterTheme.Accent.YELLOW, TwitterTheme.resolveAccent("yellow"));
        assertEquals(TwitterTheme.Accent.PINK, TwitterTheme.resolveAccent("pink"));
        assertEquals(TwitterTheme.Accent.PURPLE, TwitterTheme.resolveAccent("purple"));
        assertEquals(TwitterTheme.Accent.ORANGE, TwitterTheme.resolveAccent("orange"));
        assertEquals(TwitterTheme.Accent.GREEN, TwitterTheme.resolveAccent("green"));
    }

    @Test
    public void unknownAccentFallsBackToBlue() {
        assertEquals(TwitterTheme.Accent.BLUE, TwitterTheme.resolveAccent("unknown"));
        assertEquals(TwitterTheme.Accent.BLUE, TwitterTheme.resolveAccent(null));
    }
}
