/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.MarkChatAsReadScope;

final class PendingReadReceipts {
    private static final int MAX_PENDING = 64;
    private final Map<Key, MarkChatAsReadScope.Operation> pending = new LinkedHashMap<>();

    synchronized void remember(Object session, String threadId, MarkChatAsReadScope.Operation receipt) {
        Key key = new Key(session, threadId);
        pending.remove(key);
        pending.put(key, receipt);
        if (pending.size() > MAX_PENDING) {
            Iterator<Key> oldest = pending.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    synchronized MarkChatAsReadScope.Operation take(Object session, String threadId) {
        return pending.remove(new Key(session, threadId));
    }

    synchronized void clear() {
        pending.clear();
    }

    private static final class Key {
        final Object session;
        final String threadId;

        Key(Object session, String threadId) {
            this.session = session;
            this.threadId = threadId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return session == key.session && threadId.equals(key.threadId);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(session) + threadId.hashCode();
        }
    }
}
