package app.morphe.extension.instagram.patches.notification.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ChatStoreTest {
    private static final String ME = "100";
    private static final String ALICE = "200";
    private static final long T = 1_793_210_400_000L;
    private final ChatKey chat = new ChatKey(ME, "t1");

    /** In-memory store holding what a SQLite store would write after forDisk. */
    static final class MemStore implements ChatStore {
        final Map<ChatKey, StoredChat> disk = new HashMap<>();
        int loads;
        int saves;

        @Override
        public StoredChat load(ChatKey key) {
            loads++;
            return disk.get(key);
        }

        @Override
        public void save(Map<ChatKey, StoredChat> chats, long now) {
            saves++;
            for (Map.Entry<ChatKey, StoredChat> e : chats.entrySet()) disk.put(e.getKey(), ChatStateCodec.forDisk(e.getValue()));
        }

        @Override
        public void forget(String viewerId) {
            disk.keySet().removeIf(k -> viewerId.equals(k.viewerId));
        }
    }

    @Test
    public void logoutForgetsOnlyThatAccount() {
        MemStore store = new MemStore();
        ChatReducer r = new ChatReducer(() -> T, store);
        ChatKey other = new ChatKey("101", "t2");
        r.onItem(chat, msg("i1", ALICE, "hi", T + 1000, T + 1000));
        r.onItem(other, msg("i2", ALICE, "hello", T + 2000, T + 2000));
        r.flush();
        assertEquals(2, store.disk.size());

        r.forgetViewer(ME);
        r.flush();
        assertEquals(java.util.Collections.singleton(other), store.disk.keySet());
        org.junit.Assert.assertNull(r.stateOf(chat));
        org.junit.Assert.assertNotNull(r.stateOf(other));
    }

    private static Message msg(String id, String sender, String text, long st, long arr) {
        return new Message(id, sender, sender.equals(ME), text, st, arr, false,
                text.startsWith("Sent a") ? Message.Kind.MEDIA : Message.Kind.TEXT);
    }

    @Test
    public void codecRoundTrip() {
        ChatReducer r = new ChatReducer(() -> T);
        RenderPlan p = r.onPost(chat, Arrays.asList(msg(null, ALICE, "hi", T + 1000, T), msg("i2", ME, "yo", T + 2000, T)),
                3, "latest", ALICE, "Alice", T, false);
        r.onDisplayed(chat, p);
        ChatState s = r.stateOf(chat);
        StoredChat a = ChatStateCodec.snapshot(s);
        ChatState back = ChatStateCodec.restore(chat, a);
        StoredChat b = ChatStateCodec.snapshot(back);
        assertEquals(a, b);
        assertEquals(s.lastPlan, back.lastPlan);
        assertEquals(s.entries.size(), back.entries.size());
        assertEquals(s.slots, back.slots);
        assertEquals("Alice", back.digestSenderName);
    }

    @Test
    public void forDiskCapsAndDropsTextOfClearedChat() {
        ChatReducer r = new ChatReducer(() -> T);
        r.onItem(chat, msg("a", ALICE, "secret", T + 1000, T));
        r.onCleared(chat);
        StoredChat c = ChatStateCodec.forDisk(ChatStateCodec.snapshot(r.stateOf(chat)));
        for (StoredChat.Held h : c.held) assertEquals("", h.msg.text);
        assertFalse(c.planMessages.size() > 0);
        // unread chats keep their text
        ChatReducer r2 = new ChatReducer(() -> T);
        r2.onItem(chat, msg("a", ALICE, "keep", T + 1000, T));
        StoredChat u = ChatStateCodec.forDisk(ChatStateCodec.snapshot(r2.stateOf(chat)));
        assertEquals("keep", u.held.get(0).msg.text);
    }

    @Test
    public void forDiskCapsAt50() {
        StoredChat c = new StoredChat();
        for (int i = 0; i < 80; i++) c.held.add(new StoredChat.Held(msg("m" + i, ALICE, "x", T + i, T), i, 0));
        assertEquals(ChatReducer.MAX_MESSAGES, ChatStateCodec.forDisk(c).held.size());
        assertEquals("m30", ChatStateCodec.forDisk(c).held.get(0).msg.itemId);
    }

    @Test
    public void restoredReducerGivesSamePlans() {
        MemStore store = new MemStore();
        ChatReducer a = new ChatReducer(() -> T, store);
        RenderPlan p1 = a.onItem(chat, msg("i1", ALICE, "one", T + 1000, T));
        a.onDisplayed(chat, p1);
        a.flush();
        RenderPlan p2 = a.onItem(chat, msg("i2", ALICE, "two", T + 2000, T));
        a.onDisplayed(chat, p2);
        a.flush();
        assertEquals(1, store.loads);

        // restart: a new reducer over the same store versus a reducer that never restarted
        ChatReducer b = new ChatReducer(() -> T, store);
        RenderPlan bPeek = b.peek(chat);
        assertEquals(a.peek(chat), bPeek);
        // a duplicate delivery is silent in both
        assertNull(a.onItem(chat, msg("i2", ALICE, "two", T + 2000, T)));
        assertNull(b.onItem(chat, msg("i2", ALICE, "two", T + 2000, T)));
        // a new message gives the same plan in both
        Message m3 = msg("i3", ALICE, "three", T + 3000, T);
        assertEquals(a.onItem(chat, m3), b.onItem(chat, m3));
        // an old message below readUpTo stays silent after clearing and restart
        a.onCleared(chat);
        a.flush();
        ChatReducer c = new ChatReducer(() -> T, store);
        RenderPlan late = c.onItem(chat, msg("i1", ALICE, "one", T + 1000, T));
        assertTrue(late == null || !late.alert);
        assertTrue(store.disk.get(chat).readUpTo >= T + 3000);
    }

    @Test
    public void flushWritesOncePerEventBatch() {
        MemStore store = new MemStore();
        ChatReducer r = new ChatReducer(() -> T, store);
        r.onItem(chat, msg("i1", ALICE, "one", T + 1000, T));
        r.onItem(new ChatKey(ME, "t2"), msg("j1", ALICE, "other", T + 1000, T));
        r.flush();
        assertEquals(1, store.saves);
        assertNotNull(store.disk.get(chat));
        assertNotNull(store.disk.get(new ChatKey(ME, "t2")));
        r.flush();
        assertEquals(1, store.saves);
    }
}
