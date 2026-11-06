package app.morphe.extension.instagram.patches.notification.state;

import java.util.Map;

/**
 * Persistence for chat state (R10). Pure interface: the SQLite implementation lives outside this
 * package. Called only from the reducer's owner thread (R2). Implementations must not throw: on any
 * error they fall back to doing nothing (R3).
 */
public interface ChatStore {
    /** The persisted state of a chat, or null when none. Called once per chat per process. */
    StoredChat load(ChatKey key);

    /** Writes the given chats, one batch. {@code now} is the phone clock for purging. */
    void save(Map<ChatKey, StoredChat> chats, long now);

    /** Deletes everything stored for an account, after it logged out. */
    default void forget(String viewerId) {
    }
}
