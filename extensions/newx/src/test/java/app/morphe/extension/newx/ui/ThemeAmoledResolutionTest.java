package app.morphe.extension.newx.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the failure where extension-owned screens rendered dim instead of black when the NewX
 * dynamic color patch was not applied, leaving its dark style chooser unregistered.
 */
public final class ThemeAmoledResolutionTest {
    @Test
    public void unregisteredDarkStyleFallsBackToNativeBlackSurfaces() {
        assertTrue(Theme.resolveAmoledBlack(false, "amoled"));
        assertTrue(Theme.resolveAmoledBlack(false, "dim"));
        assertTrue(Theme.resolveAmoledBlack(false, "unexpected"));
    }

    @Test
    public void registeredDarkStyleFollowsTheUserChoice() {
        assertTrue(Theme.resolveAmoledBlack(true, "amoled"));
        assertFalse(Theme.resolveAmoledBlack(true, "dim"));
    }

    @Test
    public void unknownStoredValueStaysOnAmoled() {
        assertTrue(Theme.resolveAmoledBlack(true, "unexpected"));
        assertTrue(Theme.resolveAmoledBlack(true, ""));
    }
}
