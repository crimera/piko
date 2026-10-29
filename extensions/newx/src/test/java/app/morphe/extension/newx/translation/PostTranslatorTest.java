package app.morphe.extension.newx.translation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Guards against translating a note's truncated timeline preview instead of its full body. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public final class PostTranslatorTest {
    @Test
    public void noteTextIsNotClippedToTheCanonicalDisplayRange() {
        String note = "This is the complete note body, including every paragraph and its ending.";

        assertEquals(note, PostTranslator.selectVisibleText("This is the preview", note, 0, 20));
        assertEquals(note, PostTranslator.selectVisibleText(null, note, 0, 0));
    }

    @Test
    public void ordinaryPostKeepsItsCodePointRangeWhenNoteIsMissingOrBlank() {
        String text = "😀hello world";

        assertEquals("hello", PostTranslator.selectVisibleText(text, null, 1, 6));
        assertEquals("hello", PostTranslator.selectVisibleText(text, "   ", 1, 6));
    }
}
