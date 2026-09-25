/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.Assert.*;

public class WireGuardStorageTest {
    @Test public void readsCompleteDocumentsIncludingExactLimit() throws Exception {
        assertArrayEquals(new byte[0], WireGuardStorage.readBounded(new ByteArrayInputStream(new byte[0]), 16));
        byte[] document = new byte[]{1, 2, 3, 4};
        assertArrayEquals(document, WireGuardStorage.readBounded(new ByteArrayInputStream(document), 4));
    }

    @Test public void rejectsOversizeDocumentWithoutReturningPartialConfiguration() throws Exception {
        try {
            WireGuardStorage.readBounded(new ByteArrayInputStream(new byte[17]), 16);
            fail("Accepted oversized document");
        } catch (IOException expected) { }
    }

    @Test public void handlesUnavailableDocumentProvider() throws Exception {
        try { WireGuardStorage.readBounded(null, 16); fail("Accepted missing document"); }
        catch (IOException expected) { }
    }
}
