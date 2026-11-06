package app.morphe.extension.instagram.patches.notification.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * All notification rules, as one method per event. Pure Java: no Android types.
 *
 * <p><b>Threading contract (R2):</b> a ChatReducer is single-threaded. Every call, including
 * {@link #peek}, must come from the one thread that owns it; the reducer has no locks. Production
 * code reaches it only through {@link ChatExecutor}. Tests call it directly.
 *
 * <p>Each event returns the new {@link RenderPlan}, or null when nothing the user can see changed.
 *
 * <p><b>New</b> (R1): a message is new iff it is from someone else and its real server time is
 * greater than {@code readUpTo}, which advances only on {@link #onCleared}, {@link #onDismissed}
 * and {@link #onReadElsewhere}. A message without a server time (a digest's own copy) is new until
 * cleared. Arrival time only orders display. The digest count N never decides "new"; it only sizes
 * the "more" line: {@code moreLine = max(0, N - unread messages held)}.
 *
 * <p><b>Alert, in one sentence:</b> a plan alerts iff it holds a message from someone else that no
 * earlier displayed plan showed, or promises more such messages than the last displayed plan did,
 * except that the real copy of a displayed digest message, a real message filling a displayed
 * "N more" promise, edits, unsends, own messages and messages older than {@code readUpTo} are silent.
 *
 * <p><b>Identity</b> (R6): item id; else sender + server time; else a digest copy (no id, no time)
 * matches a real item of the same sender within two minutes, one-to-one: by equal text, or, when the
 * digest wording is generic media ("Sent a photo"), only a real item of kind MEDIA. Two real items
 * with different ids never merge.
 */
public final class ChatReducer {
    public static final int MAX_MESSAGES = 50;
    public static final long TEXT_MATCH_WINDOW_MS = 2 * 60_000L;
    /** How long a read digest copy waits for its late real copy. */
    public static final long PLACEHOLDER_TTL_MS = 2 * 60 * 60_000L;
    public static final int MAX_PLACEHOLDERS = 20;

    private static final Pattern SENT_PREFIX = Pattern.compile("^Sent a message: ");
    private static final Pattern GENERIC_MEDIA = Pattern.compile("^Sent an? [A-Za-z ]{1,30}$");
    private static final Pattern TO_CHAT_PREFIX = Pattern.compile("^[^:\\n]{1,80} to [^:\\n]{1,80}: ");

    private static final Comparator<ChatState.Entry> ORDER = new Comparator<ChatState.Entry>() {
        @Override
        public int compare(ChatState.Entry a, ChatState.Entry b) {
            int c = Long.compare(a.msg.displayTime(), b.msg.displayTime());
            return c != 0 ? c : Long.compare(a.seq, b.seq);
        }
    };

    private final LongSupplier clock;
    private final Map<ChatKey, ChatState> states = new HashMap<>();
    private final ChatStore store;
    private final java.util.Set<ChatKey> loaded = new java.util.HashSet<>();
    private final java.util.Set<ChatKey> dirty = new java.util.LinkedHashSet<>();
    /** The entry the last {@link #ingest} merged into or created, null when it changed nothing. */
    private ChatState.Entry lastTouched;

    public ChatReducer(LongSupplier clock, ChatStore store) {
        this.clock = clock;
        this.store = store;
    }

    public ChatReducer(LongSupplier clock) {
        this(clock, null);
    }

    public ChatReducer() {
        this(System::currentTimeMillis, null);
    }

    // ---------------------------------------------------------------- events

    /**
     * A push digest. {@code isPlaceholder} means the text is only "N new messages" (no real text);
     * {@code arrivalTime} is the phone clock. A digest has no server time, so its own message gets 0.
     */
    public RenderPlan onDigest(ChatKey key, int digestCount, String digestText, String digestSenderId,
                               String digestSenderName, long arrivalTime, boolean isPlaceholder) {
        ChatState s = state(key);
        prune(s);
        applyDigest(s, Math.max(1, digestCount), digestText, digestSenderId, digestSenderName, arrivalTime,
                isPlaceholder);
        enforceCap(s);
        return publish(s, build(s, true));
    }

    /** A message from realtime or REST. */
    public RenderPlan onItem(ChatKey key, Message m) {
        ChatState s = state(key);
        prune(s);
        if (!ingest(s, m)) return null;
        enforceCap(s);
        return publish(s, build(s, true));
    }

    /**
     * A notification Instagram posted: the messages it holds (items without ids; their times are real
     * server times or 0) plus its digest when {@code digestCount > 0}. Unlike the other events this
     * always returns a plan, even an unchanged one, because Instagram's record has to be rendered
     * (the second post of a group, or a re-post, replaces what was on screen).
     */
    public RenderPlan onPost(ChatKey key, List<Message> items, int digestCount, String digestText,
                             String digestSenderId, String digestSenderName, long arrivalTime,
                             boolean isPlaceholder) {
        ChatState s = state(key);
        prune(s);
        java.util.Set<ChatState.Entry> inPost = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ChatState.Entry, Boolean>());
        long newest = 0;
        for (Message m : items) {
            ingest(s, m);
            if (lastTouched != null) inPost.add(lastTouched);
            if (m.hasServerTime()) newest = Math.max(newest, m.serverTime);
        }
        // Instagram's post decides what is notifiable: a held message older than the newest message
        // it shows, which the post does not contain, was read or is old sync data. Marked one by one,
        // not through readUpTo, so a late real delivery with an earlier time is still judged on its own.
        // A message already shown is exempt: a new post of a chat holds only its newest message, and a
        // shown message that was read would have been cleared by a cancel, a swipe or a read marker.
        if (newest > 0) {
            for (ChatState.Entry e : s.entries) {
                if (!e.cleared && !e.displayed && !inPost.contains(e) && e.msg.hasServerTime()
                        && e.msg.serverTime < newest) {
                    e.cleared = true;
                }
            }
        }
        if (digestCount > 0) {
            applyDigest(s, digestCount, digestText, digestSenderId, digestSenderName, arrivalTime, isPlaceholder);
        }
        enforceCap(s);
        RenderPlan p = build(s, true);
        s.lastPlan = p;
        return p;
    }

    private void applyDigest(ChatState s, int n, String digestText, String digestSenderId,
                             String digestSenderName, long arrivalTime, boolean isPlaceholder) {
        if (!isPlaceholder && digestText != null && !digestText.isEmpty()) {
            boolean viewer = digestSenderId != null && digestSenderId.equals(s.key.viewerId);
            String shown = SENT_PREFIX.matcher(digestText.trim()).replaceFirst("");
            merge(s, new Message(null, digestSenderId, viewer, shown, 0, arrivalTime));
            s.digestText = digestText;
        } else {
            s.digestText = null;
        }
        s.digestCount = n;
        s.digestSenderId = digestSenderId;
        s.digestSenderName = digestSenderName;
        s.digestArrival = arrivalTime;
    }

    /** Takes one message in. Returns true if what the user would see changed. */
    private boolean ingest(ChatState s, Message m) {
        lastTouched = null;
        if (m.hasServerTime() && m.serverTime <= Math.max(s.readUpTo, s.replyReadUpTo)) {
            // Read already. If it is the real copy of a read placeholder, retire the placeholder.
            for (Iterator<ChatState.Entry> it = s.entries.iterator(); it.hasNext(); ) {
                ChatState.Entry e = it.next();
                if (e.cleared && isBare(e.msg) && matches(e, m)) {
                    it.remove();
                    break;
                }
            }
            return false;
        }
        boolean changed = merge(s, m);
        ChatState.Entry e = lastTouched;
        // Replying means the user saw everything before the reply.
        if (e != null && !e.cleared && e.msg.senderIsViewer && e.msg.hasServerTime()) {
            changed |= readBefore(s, e.msg.serverTime);
        }
        return changed;
    }

    /**
     * Everything sent before the user's own message at {@code ownTime} is read: it never alerts nor
     * counts as unread. What a record already shows stays as context until the record is cleared;
     * what was never shown is dropped.
     */
    private boolean readBefore(ChatState s, long ownTime) {
        int dropped = 0;
        int marked = 0;
        for (ChatState.Entry e : s.entries) {
            if (e.cleared || e.read || !e.msg.hasServerTime() || e.msg.serverTime >= ownTime) continue;
            if (e.displayed) e.read = true;
            else {
                e.cleared = true;
                dropped++;
            }
            if (!e.msg.senderIsViewer) marked++;
        }
        s.replyReadUpTo = Math.max(s.replyReadUpTo, ownTime - 1);
        if (s.digestCount > 0) s.digestCount = Math.max(0, s.digestCount - marked);
        prune(s);
        return dropped > 0;
    }

    /** A message was unsent: remove it silently. */
    public RenderPlan onUnsend(ChatKey key, String itemId) {
        ChatState s = mutable(key);
        if (s == null || itemId == null) return null;
        Iterator<ChatState.Entry> it = s.entries.iterator();
        while (it.hasNext()) {
            ChatState.Entry e = it.next();
            if (!itemId.equals(e.msg.itemId)) continue;
            it.remove();
            if (e.cleared) return null;
            if (!e.msg.senderIsViewer && s.digestCount > 0) s.digestCount--;
            RenderPlan p = build(s, false);
            s.slots = Math.min(s.slots, p.moreLine);
            return publish(s, p);
        }
        return null;
    }

    /** A message was edited: replace its text in place, keep position, no alert. */
    public RenderPlan onEdit(ChatKey key, String itemId, String newText) {
        ChatState s = mutable(key);
        if (s == null || itemId == null || newText == null) return null;
        for (ChatState.Entry e : s.entries) {
            if (e.cleared || !itemId.equals(e.msg.itemId)) continue;
            if (e.msg.text.equals(newText)) return null;
            e.msg = e.msg.withText(newText, true);
            return publish(s, build(s, false));
        }
        return null;
    }

    /** The chat was read on another device up to this real server time. */
    public RenderPlan onReadElsewhere(ChatKey key, long upToServerTime) {
        if (upToServerTime <= 0) return null;
        ChatState s = state(key);
        s.readUpTo = Math.max(s.readUpTo, upToServerTime);
        int clearedOthers = 0;
        for (ChatState.Entry e : s.entries) {
            if (e.cleared) continue;
            long t = e.msg.hasServerTime() ? e.msg.serverTime : e.msg.arrivalTime;
            if (t <= upToServerTime) {
                e.cleared = true;
                if (!e.msg.senderIsViewer) clearedOthers++;
            }
        }
        if (s.digestCount > 0) {
            s.digestCount = s.digestArrival > 0 && s.digestArrival <= upToServerTime
                    ? 0 : Math.max(0, s.digestCount - clearedOthers);
        }
        prune(s);
        RenderPlan p = build(s, false);
        s.slots = Math.min(s.slots, p.moreLine);
        return publish(s, p);
    }

    /** The record was cancelled or marked read on this device: everything held is now read. */
    public RenderPlan onCleared(ChatKey key) {
        ChatState s = mutable(key);
        if (s == null) return null;
        long newest = 0;
        boolean had = s.digestCount > 0;
        for (ChatState.Entry e : s.entries) {
            newest = Math.max(newest, e.msg.serverTime);
            if (!e.cleared) {
                e.cleared = true;
                had = true;
            }
        }
        s.readUpTo = Math.max(s.readUpTo, newest);
        s.digestCount = 0;
        s.digestText = null;
        s.slots = 0;
        prune(s);
        if (!had && (s.lastPlan == null || s.lastPlan.clear)) return null;
        RenderPlan p = RenderPlan.clearPlan();
        s.lastPlan = p;
        return p;
    }

    /** The user swiped the record away. Same as {@link #onCleared}. */
    public RenderPlan onDismissed(ChatKey key) {
        return onCleared(key);
    }

    /** The renderer posted this plan: what it showed is now "displayed", and its promises are known. */
    public void onDisplayed(ChatKey key, RenderPlan plan) {
        ChatState s = mutable(key);
        if (s == null || plan == null) return;
        s.lastPlan = plan;
        if (plan.clear) return;
        for (Message pm : plan.messages) {
            for (ChatState.Entry e : s.entries) {
                if (!e.cleared && !e.displayed && sig(e.msg).equals(sig(pm))) {
                    e.displayed = true;
                    break;
                }
            }
        }
        s.slots = plan.moreLine;
    }

    /** The plan for the current state without alerting, or null if the chat has nothing to show. */
    public RenderPlan peek(ChatKey key) {
        ChatState s = existing(key);
        if (s == null) return null;
        RenderPlan p = build(s, false);
        return p.clear ? null : p;
    }

    // ---- Queries for read markers and actions (what the chat holds, nothing changes) ----

    /** Server time of an item someone else sent, when the chat holds it; 0 otherwise. */
    public long itemTime(ChatKey key, String itemId) {
        ChatState s = existing(key);
        if (s == null || itemId == null) return 0;
        for (ChatState.Entry e : s.entries) {
            if (!e.msg.senderIsViewer && itemId.equals(e.msg.itemId)) return e.msg.serverTime;
        }
        return 0;
    }

    /** The newest message someone else sent that the chat holds with an item id; null when none. */
    public Message latestFromOthers(ChatKey key) {
        ChatState s = existing(key);
        if (s == null) return null;
        Message latest = null;
        for (ChatState.Entry e : s.entries) {
            Message m = e.msg;
            if (m.senderIsViewer || m.itemId == null || !m.hasServerTime()) continue;
            if (latest == null || m.serverTime > latest.serverTime) latest = m;
        }
        return latest;
    }

    /** The chat of the given account holding an item, among the chats in memory; null when none. */
    public ChatKey chatOfItem(String viewerId, String itemId) {
        if (itemId == null) return null;
        for (Map.Entry<ChatKey, ChatState> en : states.entrySet()) {
            if (viewerId != null && !viewerId.equals(en.getKey().viewerId)) continue;
            for (ChatState.Entry e : en.getValue().entries) {
                if (itemId.equals(e.msg.itemId)) return en.getKey();
            }
        }
        return null;
    }

    /** For tests and diagnostics. */
    public ChatState stateOf(ChatKey key) {
        return existing(key);
    }

    // ------------------------------------------------------------- internals

    /** The chat's state, loaded from the store on first use; null when the chat is unknown. */
    private ChatState existing(ChatKey key) {
        ChatState s = states.get(key);
        if (s == null && store != null && !loaded.contains(key)) {
            loaded.add(key);
            StoredChat sc = null;
            try {
                sc = store.load(key);
            } catch (RuntimeException ex) {
                // a store must not throw; treat as unknown
            }
            if (sc != null) {
                s = ChatStateCodec.restore(key, sc);
                states.put(key, s);
            }
        }
        return s;
    }

    private ChatState mutable(ChatKey key) {
        ChatState s = existing(key);
        if (s != null) dirty.add(key);
        return s;
    }

    /**
     * The account logged out: its chats are dropped from memory and from the store, so nothing of
     * it is shown or written again. Other accounts are untouched.
     */
    public void forgetViewer(String viewerId) {
        if (viewerId == null) return;
        states.keySet().removeIf(k -> viewerId.equals(k.viewerId));
        loaded.removeIf(k -> viewerId.equals(k.viewerId));
        dirty.removeIf(k -> viewerId.equals(k.viewerId));
        if (store == null) return;
        try {
            store.forget(viewerId);
        } catch (RuntimeException ex) {
            // a store must not throw
        }
    }

    /** Writes every chat an event touched to the store (one batch). Called by the owner thread after each event. */
    public void flush() {
        if (store == null || dirty.isEmpty()) {
            dirty.clear();
            return;
        }
        Map<ChatKey, StoredChat> batch = new java.util.LinkedHashMap<>();
        for (ChatKey k : dirty) {
            ChatState s = states.get(k);
            if (s != null) batch.put(k, ChatStateCodec.snapshot(s));
        }
        dirty.clear();
        try {
            store.save(batch, clock.getAsLong());
        } catch (RuntimeException ex) {
            // a store must not throw; state stays in memory
        }
    }

    private ChatState state(ChatKey key) {
        ChatState s = existing(key);
        if (s == null) {
            s = new ChatState(key);
            states.put(key, s);
        }
        dirty.add(key);
        return s;
    }

    private static String sig(Message m) {
        return (m.senderIsViewer ? "me" : m.senderId) + "|" + m.itemId + "|" + m.serverTime + "|"
                + m.arrivalTime + "|" + m.text;
    }

    private static int unreadOthers(ChatState s) {
        int n = 0;
        for (ChatState.Entry e : s.entries) if (!e.cleared && !e.read && !e.msg.senderIsViewer) n++;
        return n;
    }

    private RenderPlan build(ChatState s, boolean allowAlert) {
        List<ChatState.Entry> vis = new ArrayList<>();
        for (ChatState.Entry e : s.entries) if (!e.cleared) vis.add(e);
        Collections.sort(vis, ORDER);
        int more = Math.max(0, s.digestCount - unreadOthers(s));
        if (vis.isEmpty() && more == 0) return RenderPlan.clearPlan();
        List<Message> out = new ArrayList<>(vis.size());
        boolean alert = more > s.slots;
        for (ChatState.Entry e : vis) {
            out.add(e.msg.forRender());
            if (!e.displayed && !e.msg.senderIsViewer) alert = true;
        }
        return new RenderPlan(out, more, more > 0 ? s.digestSenderName : null, allowAlert && alert, false);
    }

    /** Returns the plan unless it equals what the renderer already has; remembers it. */
    private RenderPlan publish(ChatState s, RenderPlan p) {
        if (p.clear) {
            if (s.lastPlan == null || s.lastPlan.clear) return null;
        } else if (s.lastPlan != null && p.sameContent(s.lastPlan) && (!p.alert || s.lastPlan.alert)) {
            return null;
        }
        s.lastPlan = p;
        return p;
    }

    /** Adds or merges a message. Returns true if what the user would see changed. */
    private boolean merge(ChatState s, Message in) {
        for (ChatState.Entry e : s.entries) {
            if (!matches(e, in)) continue;
            lastTouched = e;
            boolean mixed = isBare(e.msg) != isBare(in);
            if (mixed) e.copyAbsorbed = true;
            if (e.cleared) {
                // A read placeholder's real copy: absorbed silently. Everything older is read too.
                e.msg = combine(e.msg, in);
                if (!in.hasServerTime()) return false;
                s.readUpTo = Math.max(s.readUpTo, in.serverTime);
                boolean any = false;
                for (ChatState.Entry o : s.entries) {
                    if (!o.cleared && o.msg.hasServerTime() && o.msg.serverTime <= s.readUpTo) {
                        o.cleared = true;
                        any = true;
                    }
                }
                prune(s);
                return any;
            }
            Message merged = combine(e.msg, in);
            if (merged.equals(e.msg)) return false;
            e.msg = merged;
            return true;
        }
        ChatState.Entry fresh = new ChatState.Entry(in, s.nextSeq++);
        // A real message from someone else arriving while a displayed promise ("N more") is open
        // fills that promise instead of announcing a new message.
        if (!isBare(in) && !in.senderIsViewer && s.slots > 0) {
            s.slots--;
            fresh.displayed = true;
        }
        s.entries.add(fresh);
        lastTouched = fresh;
        return true;
    }

    private static boolean isBare(Message m) {
        return m.itemId == null && !m.hasServerTime();
    }

    private static int richness(Message m) {
        return (m.itemId != null ? 2 : 0) + (m.hasServerTime() ? 1 : 0);
    }

    private static Message combine(Message old, Message in) {
        int ro = richness(old), ri = richness(in);
        boolean edited = old.edited || in.edited;
        String text;
        if (ri > ro) text = in.text;
        else if (ri < ro) text = old.text;
        else if (ri >= 2) {
            // Same item id delivered again with other text is an edit.
            text = in.text.isEmpty() ? old.text : in.text;
            if (!text.equals(old.text)) edited = true;
        } else text = old.text;
        // Instagram's notification names the media ("Sent a sticker"); the stored item only has our
        // label. The copy without an item id is Instagram's, so its wording is kept.
        Message labelled = text.equals(old.text) ? old : in;
        Message other = labelled == old ? in : old;
        if (labelled.itemId != null && other.itemId == null && Message.isLabel(text)
                && !other.text.isEmpty() && !Message.ATTACHMENT.equals(other.text)) {
            text = other.text;
        }
        return new Message(old.itemId != null ? old.itemId : in.itemId, old.senderId,
                old.senderIsViewer, text, old.serverTime > 0 ? old.serverTime : in.serverTime,
                old.arrivalTime, edited, ri > ro ? in.kind : old.kind);
    }

    /** R6 identity of a held entry against an incoming message. */
    private static boolean matches(ChatState.Entry e, Message in) {
        Message a = e.msg;
        if (a.itemId != null && in.itemId != null) return a.itemId.equals(in.itemId);
        if (!sameSender(a, in)) return false;
        if (a.hasServerTime() && in.hasServerTime()) return a.serverTime == in.serverTime;
        boolean aBare = isBare(a), bBare = isBare(in);
        if (!aBare && !bBare) return false;
        if (!near(a, in)) return false;
        if (aBare && bBare) return normalize(a.text).equals(normalize(in.text));
        Message bare = aBare ? a : in;
        Message real = aBare ? in : a;
        // One-to-one: a real item that already absorbed a digest copy takes no second one.
        if (!aBare && e.copyAbsorbed) return false;
        if (GENERIC_MEDIA.matcher(normalize(bare.text)).matches()) {
            return real.kind == Message.Kind.MEDIA;
        }
        return normalize(a.text).equals(normalize(in.text));
    }

    private static boolean sameSender(Message a, Message b) {
        if (a.senderIsViewer || b.senderIsViewer) return a.senderIsViewer && b.senderIsViewer;
        return a.senderId != null && a.senderId.equals(b.senderId);
    }

    /** Within two minutes by phone arrival, or by the real copy's send time against the digest's arrival. */
    private static boolean near(Message a, Message b) {
        long[] ta = times(a), tb = times(b);
        for (long x : ta) for (long y : tb) if (Math.abs(x - y) <= TEXT_MATCH_WINDOW_MS) return true;
        return false;
    }

    private static long[] times(Message m) {
        return m.hasServerTime() ? new long[]{m.arrivalTime, m.serverTime} : new long[]{m.arrivalTime};
    }

    static String normalize(String t) {
        String r = SENT_PREFIX.matcher(t.trim()).replaceFirst("");
        return TO_CHAT_PREFIX.matcher(r).replaceFirst("").trim();
    }

    private void enforceCap(ChatState s) {
        while (s.entries.size() > MAX_MESSAGES) {
            ChatState.Entry victim = null;
            for (ChatState.Entry e : s.entries) {
                if (victim == null || (e.cleared && !victim.cleared)
                        || (e.cleared == victim.cleared && ORDER.compare(e, victim) < 0)) victim = e;
            }
            s.entries.remove(victim);
        }
    }

    /** Drops read entries that are no longer needed: covered by readUpTo, or placeholders too old/too many. */
    private void prune(ChatState s) {
        long now = clock.getAsLong();
        int placeholders = 0;
        Iterator<ChatState.Entry> it = s.entries.iterator();
        while (it.hasNext()) {
            ChatState.Entry e = it.next();
            if (!e.cleared) continue;
            if (e.msg.hasServerTime()) {
                if (e.msg.serverTime <= s.readUpTo) it.remove();
            } else if (now - e.msg.arrivalTime > PLACEHOLDER_TTL_MS) {
                it.remove();
            } else {
                placeholders++;
            }
        }
        while (placeholders > MAX_PLACEHOLDERS) {
            ChatState.Entry oldest = null;
            for (ChatState.Entry e : s.entries) {
                if (e.cleared && !e.msg.hasServerTime() && (oldest == null || ORDER.compare(e, oldest) < 0)) oldest = e;
            }
            s.entries.remove(oldest);
            placeholders--;
        }
    }
}
