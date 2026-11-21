package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class NewXDownloaderTest {

    @Test
    public void largerVariantUrlReplacesOrigWith4096() {
        assertEquals(
                "https://pbs.twimg.com/media/test.jpg?format=jpg&name=4096x4096",
                NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg&name=orig")
        );
    }

    @Test
    public void largerVariantUrlReturnsNullWhenNotOrig() {
        assertNull(NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg&name=small"));
        assertNull(NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg"));
    }

    @Test
    public void largerVariantUrlReturnsNullForNull() {
        assertNull(NewXDownloader.largerVariantUrl(null));
    }
}
