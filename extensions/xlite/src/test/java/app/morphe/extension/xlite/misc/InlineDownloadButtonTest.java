package app.morphe.extension.xlite.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class InlineDownloadButtonTest {
    @Test
    public void singleMediaUsesRequestedTwitterFilename() {
        assertEquals(
                "jack_123456789.jpg",
                InlineDownloadButton.downloadFileName("jack", "123456789", "jpg", 0, 1)
        );
    }

    @Test
    public void multipleMediaUsesOneBasedSuffix() {
        assertEquals(
                "jack_123456789_1.jpg",
                InlineDownloadButton.downloadFileName("jack", "123456789", "jpg", 0, 4)
        );
        assertEquals(
                "jack_123456789_4.mp4",
                InlineDownloadButton.downloadFileName("jack", "123456789", "mp4", 3, 4)
        );
    }

    @Test
    public void filenameSegmentsAreSanitized() {
        assertEquals(
                "jack_user_post_id.jpg",
                InlineDownloadButton.downloadFileName("@jack/user", "post:id", "jpg", 0, 1)
        );
    }

    @Test
    public void temporarySuffixPrecedesExtension() {
        assertEquals(
                "jack_123456789_tmp.jpg",
                InlineDownloadButton.temporaryDownloadFileName("jack_123456789.jpg")
        );
        assertEquals(
                "download_tmp",
                InlineDownloadButton.temporaryDownloadFileName("download")
        );
    }

    @Test
    public void detectsWhetherPostHasMedia() {
        assertFalse(InlineDownloadButton.hasMedia(null));
        assertFalse(InlineDownloadButton.hasMedia(new MediaPost(null)));
        assertFalse(InlineDownloadButton.hasMedia(new MediaPost(Collections.emptyList())));
        assertTrue(InlineDownloadButton.hasMedia(new MediaPost(Collections.singletonList(new Object()))));
    }

    @Test
    public void conflictCleanupExcludesPublishedDestination() {
        assertTrue(InlineDownloadButton.existingMediaSelection().contains("!=?"));
        assertArrayEquals(
                new String[]{"jack_123456789.jpg", "Pictures/Twitter/", "42"},
                InlineDownloadButton.existingMediaSelectionArgs(
                        "jack_123456789.jpg",
                        "Pictures/Twitter/",
                        "42"
                )
        );
    }

    public static final class MediaPost {
        private final List<?> media;

        MediaPost(List<?> media) {
            this.media = media;
        }

        public List<?> getMedia() {
            return media;
        }
    }
}
