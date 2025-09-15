package androidx.emoji.text;

public class EmojiCompat {

    private static EmojiCompat sInstance;

    public static abstract class Config {
        public Config(MetadataRepoLoader loader) {
            // no-op stub
        }
    }

    public interface InitCallback {
        void onInitialized();
        void onFailed(Throwable throwable);
    }

    public static EmojiCompat init(Config config) {
        if (sInstance == null) {
            sInstance = new EmojiCompat();
        }
        return sInstance;
    }

    public static EmojiCompat get() {
        if (sInstance == null) {
            throw new IllegalStateException("EmojiCompat not initialized");
        }
        return sInstance;
    }

    public boolean hasEmojiGlyph(CharSequence sequence) {
        // Stub implementation
        return true;
    }

    public CharSequence process(CharSequence sequence) {
        // Just return input as-is in stub
        return sequence;
    }
}
