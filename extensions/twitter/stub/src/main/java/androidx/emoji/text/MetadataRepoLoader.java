package androidx.emoji.text;

public interface MetadataRepoLoader {
    interface Callback {
        void onLoaded(MetadataRepo repo);
        void onFailed(Throwable throwable);
    }

    void load(Callback callback);
}
