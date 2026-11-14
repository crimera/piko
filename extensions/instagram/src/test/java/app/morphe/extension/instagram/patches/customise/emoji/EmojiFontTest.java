package app.morphe.extension.instagram.patches.customise.emoji;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
}
