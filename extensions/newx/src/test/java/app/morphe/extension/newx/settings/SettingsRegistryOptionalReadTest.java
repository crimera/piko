package app.morphe.extension.newx.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

public final class SettingsRegistryOptionalReadTest {
    @Test
    public void missingContributionsUseNeutralDefaultsBeforeRegistryLoad() {
        Set<String> defaultValues = Collections.singleton("default");

        assertFalse(SettingsRegistry.getBooleanOrDefault("newx.test.missing_boolean"));
        assertTrue(SettingsRegistry.getBooleanOrDefault(
                "newx.test.missing_boolean",
                true
        ));
        assertFalse(SettingsRegistry.getBooleanOrDefault(
                "newx.test.missing_boolean",
                false
        ));
        assertEquals("", SettingsRegistry.getStringOrDefault("newx.test.missing_string"));
        assertEquals(
                "fallback",
                SettingsRegistry.getStringOrDefault("newx.test.missing_string", "fallback")
        );
        assertTrue(SettingsRegistry.getStringSetOrDefault("newx.test.missing_string_set").isEmpty());
        assertSame(
                defaultValues,
                SettingsRegistry.getStringSetOrDefault(
                        "newx.test.missing_string_set",
                        defaultValues
                )
        );
    }
}
