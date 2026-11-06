package app.morphe.extension.instagram.patches.notification.state;

import java.util.Objects;

/** Identity of one chat as seen by one account. Two accounts never share state (audit A6). */
public final class ChatKey {
    public final String viewerId;
    public final String threadId;

    public ChatKey(String viewerId, String threadId) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.threadId = Objects.requireNonNull(threadId, "threadId");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatKey)) return false;
        ChatKey k = (ChatKey) o;
        return viewerId.equals(k.viewerId) && threadId.equals(k.threadId);
    }

    @Override
    public int hashCode() {
        return 31 * viewerId.hashCode() + threadId.hashCode();
    }

    @Override
    public String toString() {
        return viewerId + "/" + threadId;
    }
}
