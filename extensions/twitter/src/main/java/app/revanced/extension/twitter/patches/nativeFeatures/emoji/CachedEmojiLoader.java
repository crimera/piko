package app.revanced.extension.twitter.patches.nativeFeatures.emoji;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.Log;

import java.io.File;

public class CachedEmojiLoader {
    private static final String TAG = "piko";
    private static final String FONT_FILE_NAME = "emoji.ttf";

    private static Typeface emojiTypeface;

    public static void init(Context context) {
        try {
            File fontFile = new File(context.getCacheDir(), FONT_FILE_NAME);
            if (!fontFile.exists()) {
                Log.e(TAG, "Emoji font not found: " + fontFile.getAbsolutePath());
                return;
            }

            emojiTypeface = Typeface.createFromFile(fontFile);
            Log.i(TAG, "Emoji font loaded: " + fontFile.getAbsolutePath());

        } catch (Throwable t) {
            Log.e(TAG, "Failed to load emoji font", t);
        }
    }

    public static CharSequence process(CharSequence input) {
        if (emojiTypeface == null || input == null) {
            return input;
        }

        SpannableString ss = new SpannableString(input);
        ss.setSpan(
                new TypefaceSpan(emojiTypeface), // our custom font
                0,
                input.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return ss;
    }

    public static Typeface getEmojiTypeface() {
        return emojiTypeface;
    }
}
