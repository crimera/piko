package app.morphe.extension.instagram.patches.customise.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TypefaceSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, manifest = Config.NONE)
public class EmojiFontSpanTest {
    private static CharSequence layout(CharSequence text) {
        return EmojiFont.applySpans(text, 0, text.length(), Typeface.DEFAULT, false);
    }

    @Test
    public void wholeStringSpanSurvivesRepeatedLayout() {
        Spanned once = (Spanned) layout("😂");
        assertEquals(1, once.getSpans(0, once.length(), TypefaceSpan.class).length);
        assertSame(once, layout(once));
    }

    @Test
    public void copyAddsOnlyMissingRunWithoutChangingSource() {
        SpannableString source = new SpannableString("😂 x 🔥");
        EmojiFont.applySpans(source, 0, 2, Typeface.DEFAULT, true);
        TypefaceSpan first = source.getSpans(0, 2, TypefaceSpan.class)[0];

        Spanned result = (Spanned) layout(source);
        assertNotSame(source, result);
        assertEquals(1, source.getSpans(0, source.length(), TypefaceSpan.class).length);
        assertSame(first, result.getSpans(0, 2, TypefaceSpan.class)[0]);
        assertEquals(1, result.getSpans(5, 7, TypefaceSpan.class).length);
        assertSame(result, layout(result));
    }

    @Test
    public void splitRunLeavesNewPlainTextUnstyled() {
        SpannableStringBuilder text = new SpannableStringBuilder("😂🔥");
        EmojiFont.applySpans(text, 0, text.length(), Typeface.DEFAULT, true);
        text.insert(2, "x");
        EmojiFont.applySpans(text, 0, text.length(), Typeface.DEFAULT, true);
        assertEquals(1, text.getSpans(0, 2, TypefaceSpan.class).length);
        assertEquals(0, text.getSpans(2, 3, TypefaceSpan.class).length);
        assertEquals(1, text.getSpans(3, 5, TypefaceSpan.class).length);
    }
}
