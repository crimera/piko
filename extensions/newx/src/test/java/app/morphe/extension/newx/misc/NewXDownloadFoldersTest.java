package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.StringSetting;

import org.junit.Before;

public final class NewXDownloadFoldersTest {

    @Before
    public void setupSettings() throws ReflectiveOperationException {
        Field field = SettingsRegistry.class.getDeclaredField("frozen");
        field.setAccessible(true);
        field.set(null, true);
    }

    @After
    public void cleanupSettings() throws ReflectiveOperationException {
        settings().remove(DownloadSettings.CONFLICT_POLICY);
    }

    @Test
    public void isUsableChecksState() {
        assertTrue(NewXDownloadFolders.isUsable(NewXDownloadFolders.DestinationState.PERSISTED));
        assertTrue(NewXDownloadFolders.isUsable(NewXDownloadFolders.DestinationState.LIVE));
        assertFalse(NewXDownloadFolders.isUsable(NewXDownloadFolders.DestinationState.UNSET));
        assertFalse(NewXDownloadFolders.isUsable(NewXDownloadFolders.DestinationState.UNUSABLE));
        assertFalse(NewXDownloadFolders.isUsable(null));
    }

    @Test
    public void settingIdMappingForBothMediaKinds() {
        assertEquals(DownloadSettings.IMAGES_TREE_URI,
                NewXDownloadFolders.treeSettingId(NewXDownloadFolders.MediaKind.IMAGES));
        assertEquals(DownloadSettings.VIDEOS_TREE_URI,
                NewXDownloadFolders.treeSettingId(NewXDownloadFolders.MediaKind.VIDEOS));
        assertEquals(DownloadSettings.IMAGES_DISPLAY_PATH,
                NewXDownloadFolders.displayPathSettingId(NewXDownloadFolders.MediaKind.IMAGES));
        assertEquals(DownloadSettings.VIDEOS_DISPLAY_PATH,
                NewXDownloadFolders.displayPathSettingId(NewXDownloadFolders.MediaKind.VIDEOS));
    }

    @Test
    public void conflictPolicyMapsSettingsValues() throws ReflectiveOperationException {
        // Default is SKIP when setting is absent
        assertEquals(ConflictPolicy.SKIP, NewXDownloadFolders.conflictPolicy());

        setConflictPolicy(DownloadSettings.CONFLICT_OVERWRITE);
        assertEquals(ConflictPolicy.OVERWRITE, NewXDownloadFolders.conflictPolicy());

        setConflictPolicy(DownloadSettings.CONFLICT_RENAME);
        assertEquals(ConflictPolicy.RENAME, NewXDownloadFolders.conflictPolicy());

        setConflictPolicy(DownloadSettings.CONFLICT_SKIP);
        assertEquals(ConflictPolicy.SKIP, NewXDownloadFolders.conflictPolicy());
    }

    @Test(expected = IllegalStateException.class)
    public void conflictPolicyFailsClosedOnUnknownValue() throws ReflectiveOperationException {
        setConflictPolicy("unexpected_policy");
        NewXDownloadFolders.conflictPolicy();
    }

    private static void setConflictPolicy(String value) throws ReflectiveOperationException {
        StringSetting setting = new StringSetting(DownloadSettings.CONFLICT_POLICY, DownloadSettings.CONFLICT_SKIP, false);
        setting.save(value);
        settings().put(DownloadSettings.CONFLICT_POLICY, setting);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settings() throws ReflectiveOperationException {
        Field field = SettingsRegistry.class.getDeclaredField("SETTINGS");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }
}
