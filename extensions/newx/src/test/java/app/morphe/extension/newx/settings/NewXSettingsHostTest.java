package app.morphe.extension.newx.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.settings.SettingsHost;
import app.morphe.extension.crimera.settings.SettingsString;

/**
 * The shared settings registry refuses to freeze when a string its UI reads is missing, which on a
 * device is a crash at startup. A library bump that adds a required string, or a rename here, would
 * only show up there, so check every key against the strings the NewX patch actually adds.
 */
public final class NewXSettingsHostTest {
    private static final String STRINGS_PATH =
            "patches/src/main/resources/addresources/values/newx/strings.xml";

    @Test
    public void everyStringTheSharedSettingsUiReadsIsAddedByTheNewXPatch() throws Exception {
        NewXSettingsHost.install();
        SettingsHost host = SettingsHost.require();
        String strings = new String(
                Files.readAllBytes(repositoryRoot().resolve(STRINGS_PATH)),
                StandardCharsets.UTF_8
        );

        List<String> missing = new ArrayList<>();
        for (SettingsString string : SettingsString.values()) {
            String name = host.resourceName(string);
            if (!strings.contains("name=\"" + name + "\"")) missing.add(name);
        }

        assertTrue("Missing from " + STRINGS_PATH + ": " + missing, missing.isEmpty());
    }

    private static Path repositoryRoot() {
        Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.exists(path.resolve(STRINGS_PATH))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root from " + System.getProperty("user.dir"));
    }
}
