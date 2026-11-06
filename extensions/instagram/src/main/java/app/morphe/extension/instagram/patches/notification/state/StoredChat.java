package app.morphe.extension.instagram.patches.notification.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Plain-data image of a {@link ChatState}, as it is stored. Immutable once built. */
public final class StoredChat {
    public static final int F_DISPLAYED = 1;
    public static final int F_CLEARED = 2;
    public static final int F_READ = 4;
    public static final int F_COPY_ABSORBED = 8;

    /** A held message with its bookkeeping. */
    public static final class Held {
        public final Message msg;
        public final long seq;
        public final int flags;

        public Held(Message msg, long seq, int flags) {
            this.msg = msg;
            this.seq = seq;
            this.flags = flags;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Held)) return false;
            Held h = (Held) o;
            return seq == h.seq && flags == h.flags && msg.equals(h.msg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(msg, seq, flags);
        }
    }

    public long readUpTo;
    public long replyReadUpTo;
    public int slots;
    public int digestCount;
    public String digestText;
    public String digestSenderId;
    public String digestSenderName;
    public long digestArrival;
    public long nextSeq;
    /** Last displayed plan, or absent. */
    public boolean hasPlan;
    public boolean planClear;
    public boolean planAlert;
    public int planMore;
    public String planSender;
    public final List<Message> planMessages = new ArrayList<>();
    public final List<Held> held = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StoredChat)) return false;
        StoredChat c = (StoredChat) o;
        return readUpTo == c.readUpTo && replyReadUpTo == c.replyReadUpTo && slots == c.slots
                && digestCount == c.digestCount && digestArrival == c.digestArrival && nextSeq == c.nextSeq
                && hasPlan == c.hasPlan && planClear == c.planClear && planAlert == c.planAlert
                && planMore == c.planMore && Objects.equals(digestText, c.digestText)
                && Objects.equals(digestSenderId, c.digestSenderId)
                && Objects.equals(digestSenderName, c.digestSenderName)
                && Objects.equals(planSender, c.planSender) && planMessages.equals(c.planMessages)
                && held.equals(c.held);
    }

    @Override
    public int hashCode() {
        return Objects.hash(readUpTo, replyReadUpTo, slots, digestCount, nextSeq, held, planMessages);
    }
}
