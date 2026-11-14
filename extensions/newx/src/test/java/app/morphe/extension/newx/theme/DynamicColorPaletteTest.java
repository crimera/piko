package app.morphe.extension.newx.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public final class DynamicColorPaletteTest {
    private static final long ORIGINAL_SURFACE = 0xFF03030300000000L;
    private static final long BLACK_SURFACE = 0xFF00000000000000L;
    private static final long WHITE_SURFACE = 0xFFFFFFFF00000000L;
    private static final long DIM_SURFACE = 0xFF15202B00000000L;

    @Test
    public void cachedHostThemeFollowsPreferenceChangesInEveryStore() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences host = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        SharedPreferences legacy = context.getSharedPreferences("x_app_preferences", Context.MODE_PRIVATE);
        host.edit().putString("dark_mode_state", "1").putString("dark_mode_appearance", "dim").commit();
        assertEquals(TwitterTheme.DIM, TwitterTheme.fromContext(context));

        host.edit().putString("dark_mode_appearance", "lights_out").apply();
        assertEquals(TwitterTheme.LIGHTS_OUT, TwitterTheme.fromContext(context));

        host.edit().remove("dark_mode_appearance").commit();
        legacy.edit().putString("dark_mode_appearance", "dim").commit();
        assertEquals(TwitterTheme.DIM, TwitterTheme.fromContext(context));

        host.edit().putString("dark_mode_state", "0").commit();
        assertEquals(TwitterTheme.STANDARD, TwitterTheme.fromContext(context));
    }

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

    @Test
    public void resourceIdsUseFrameworkFallbackAndKeyItSeparately() {
        Resources resources = new CountingResources(
                RuntimeEnvironment.getApplication().getResources()
        );
        String packageName = RuntimeEnvironment.getApplication().getPackageName();
        String missingAppName = "newx_test_missing_dynamic_color";
        int black = resources.getIdentifier("black", "color", "android");
        int white = resources.getIdentifier("white", "color", "android");

        assertEquals(black, DynamicColorPalette.resourceId(
                resources, packageName, missingAppName, "black"));
        assertEquals(black, DynamicColorPalette.resourceId(
                resources, packageName, missingAppName, "black"));
        assertEquals(white, DynamicColorPalette.resourceId(
                resources, packageName, missingAppName, "white"));
        assertNotEquals(black, white);
        assertEquals(black, DynamicColorPalette.resourceId(
                resources, "android", "black", "white"));
    }

    @Test
    public void missingResourceFailsAndDoesNotPoisonLaterFallbackLookup() {
        CountingResources resources = new CountingResources(
                RuntimeEnvironment.getApplication().getResources()
        );
        String packageName = RuntimeEnvironment.getApplication().getPackageName();
        String missingAppName = "newx_test_missing_dynamic_color";

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                DynamicColorPalette.resourceId(resources, packageName, missingAppName, null));
        assertEquals(
                "NewX dynamic color resource is missing on Android 12+: " + missingAppName,
                error.getMessage()
        );
        assertEquals(
                resources.getIdentifier("black", "color", "android"),
                DynamicColorPalette.resourceId(resources, packageName, missingAppName, "black")
        );
    }

    @Test
    public void resourceIdCacheInvalidatesForConfigurationAndSerializesConcurrentReads()
            throws Exception {
        CountingResources resources = new CountingResources(
                RuntimeEnvironment.getApplication().getResources()
        );
        int expected = resources.getIdentifier("black", "color", "android");
        resources.identifierCalls = 0;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> lookups = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                lookups.add(() -> DynamicColorPalette.resourceId(
                        resources, "android", "black", null));
            }
            for (Future<Integer> result : executor.invokeAll(lookups)) {
                assertEquals(expected, result.get().intValue());
            }
            assertEquals(1, resources.identifierCalls);

            Configuration changed = new Configuration(resources.getConfiguration());
            changed.fontScale = changed.fontScale == 1f ? 1.1f : 1f;
            resources.updateConfiguration(changed, resources.getDisplayMetrics());
            assertEquals(expected, DynamicColorPalette.resourceId(
                    resources, "android", "black", null));
            assertEquals(2, resources.identifierCalls);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class CountingResources extends Resources {
        int identifierCalls;

        CountingResources(Resources resources) {
            super(
                    resources.getAssets(),
                    resources.getDisplayMetrics(),
                    new Configuration(resources.getConfiguration())
            );
        }

        @Override
        public int getIdentifier(String name, String defType, String defPackage) {
            identifierCalls++;
            return super.getIdentifier(name, defType, defPackage);
        }
    }
}
