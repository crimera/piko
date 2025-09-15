package androidx.emoji.text;

import android.graphics.Typeface;

public class MetadataRepo {

    private final Typeface typeface;

    private MetadataRepo(Typeface tf) {
        this.typeface = tf;
    }

    public static MetadataRepo create(Typeface typeface) {
        // Stub: return wrapper around the Typeface
        return new MetadataRepo(typeface);
    }

    public Typeface getTypeface() {
        return typeface;
    }
}
