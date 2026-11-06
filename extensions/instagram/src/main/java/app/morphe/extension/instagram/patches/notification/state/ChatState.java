package app.morphe.extension.instagram.patches.notification.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state of one chat. Owned by {@link ChatReducer}; do not touch from other threads.
 * Fields are package-private on purpose: all rules live in the reducer.
 */
public final class ChatState {
    /** A held message plus bookkeeping. Cleared entries are kept briefly as tombstones for dedupe. */
    static final class Entry {
        Message msg;
        final long seq;
        /** Was in a displayed plan, or fills a displayed promise: showing it again is silent. */
        boolean displayed;
        /** Read. A cleared entry without a server time is a "read placeholder" for its late real copy. */
        boolean cleared;
        /** Read (the user replied after it) but still shown as context until the record is cleared. */
        boolean read;
        /** A digest copy and its real item were matched into this entry (one-to-one). */
        boolean copyAbsorbed;

        Entry(Message msg, long seq) {
            this.msg = msg;
            this.seq = seq;
        }
    }

    final ChatKey key;
    final List<Entry> entries = new ArrayList<>();
    long nextSeq;
    /** Real server times at or below this are read (cleared, dismissed, read elsewhere): never new. */
    long readUpTo;
    /** Real server times below the user's latest reply: read, but a displayed one stays shown. */
    long replyReadUpTo;
    /** Digest promises (placeholder "N more") the last displayed plan already showed and not yet filled. */
    int slots;
    /** Unread count the latest digest promised (0 when none). */
    int digestCount;
    String digestText;
    String digestSenderId;
    String digestSenderName;
    long digestArrival;
    RenderPlan lastPlan;

    ChatState(ChatKey key) {
        this.key = key;
    }

    public ChatKey key() { return key; }
    public long readUpTo() { return readUpTo; }
    public int digestCount() { return digestCount; }
    public String digestText() { return digestText; }
    public String digestSenderId() { return digestSenderId; }
    public String digestSenderName() { return digestSenderName; }
    public int slots() { return slots; }

    /** Read digest copies still waiting for their real copy. */
    public int heldPlaceholders() {
        int n = 0;
        for (Entry e : entries) if (e.cleared && !e.msg.hasServerTime()) n++;
        return n;
    }

    /** Number of messages not cleared (what a plan would show). */
    public int heldCount() {
        int n = 0;
        for (Entry e : entries) if (!e.cleared) n++;
        return n;
    }
}
