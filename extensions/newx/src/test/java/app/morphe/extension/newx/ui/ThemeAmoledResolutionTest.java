package app.morphe.extension.newx.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the failure where extension-owned screens rendered dim instead of black when the NewX
 * dynamic color patch was not applied, leaving its AMOLED toggle unregistered.
 */
public final class ThemeAmoledResolutionTest {
    @Test
    public void unregisteredAmoledSettingFallsBackToNativeBlackSurfaces() {
        assertTrue(Theme.resolveAmoledBlack(false, false));
        assertTrue(Theme.resolveAmoledBlack(false, true));
    }

    @Test
    public void registeredAmoledSettingFollowsTheUserPreference() {
        assertTrue(Theme.resolveAmoledBlack(true, true));
        assertFalse(Theme.resolveAmoledBlack(true, false));
    }
}
