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
public class EmojiFontTest {

    /** [start, end) of each emoji run, as the spans would cover them. */
    private static String runs(String text) {
        StringBuilder out = new StringBuilder();
        int i = EmojiFont.nextEmoji(text, 0);
        while (i >= 0) {
            int end = EmojiFont.emojiEnd(text, i);
            out.append('[').append(text, i, end).append(']');
            i = EmojiFont.nextEmoji(text, end);
        }
        return out.toString();
    }

    @Test
    public void plainTextHasNoEmoji() {
        assertEquals("", runs("see you at 9 #tonight * ok (c) 2026"));
    }

    @Test
    public void enclosedLettersAndNumbersKeepTheirTextFont() {
        assertEquals("", runs("🄰 🄀 🄰️"));
    }

    @Test
    public void supplementaryTextEmojiNeedsEmojiPresentation() {
        assertEquals("[🖥️]", runs("🖥 🖥︎ 🖥️"));
    }

    @Test
    public void joinerDoesNotPullPlainTextIntoEmojiRun() {
        assertEquals("[😀]", runs("😀\u200DA"));
    }

    @Test
    public void supplementaryEmoji() {
        assertEquals("[😂]", runs("lol 😂 ok"));
    }

    @Test
    public void adjacentEmojiShareOneRun() {
        assertEquals("[😂🔥]", runs("😂🔥"));
    }

    @Test
    public void zwjSequenceAndSkinTone() {
        String family = "👩‍👩‍👧";
        String thumbs = "👍🏽";
        assertEquals("[" + family + "][" + thumbs + "]", runs(family + " x " + thumbs));
    }

    @Test
    public void heartNeedsVariationSelector() {
        assertEquals("[❤️]", runs("❤️ and ❤"));
    }

    @Test
    public void defaultPresentationBmp() {
        assertEquals("[⭐][✅]", runs("⭐ a ✅"));
    }

    @Test
    public void textPresentationIsSkipped() {
        assertEquals("", runs("⭐︎"));
    }

    @Test
    public void keycap() {
        assertEquals("[1️⃣]", runs("room 1️⃣ not 1"));
    }

    @Test
    public void flag() {
        assertEquals("[🇯🇵]", runs("from 🇯🇵!"));
    }

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
