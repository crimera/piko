package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NewXDownloadFoldersTest {

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
}
