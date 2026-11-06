package app.morphe.extension.instagram.patches.notification.state;

import java.util.List;

/** Converts between {@link ChatState} and {@link StoredChat}. Pure Java. */
public final class ChatStateCodec {
    private ChatStateCodec() {}

    public static StoredChat snapshot(ChatState s) {
        StoredChat c = new StoredChat();
        c.readUpTo = s.readUpTo;
        c.replyReadUpTo = s.replyReadUpTo;
        c.slots = s.slots;
        c.digestCount = s.digestCount;
        c.digestText = s.digestText;
        c.digestSenderId = s.digestSenderId;
        c.digestSenderName = s.digestSenderName;
        c.digestArrival = s.digestArrival;
        c.nextSeq = s.nextSeq;
        for (ChatState.Entry e : s.entries) {
            int f = (e.displayed ? StoredChat.F_DISPLAYED : 0) | (e.cleared ? StoredChat.F_CLEARED : 0)
                    | (e.read ? StoredChat.F_READ : 0) | (e.copyAbsorbed ? StoredChat.F_COPY_ABSORBED : 0);
            c.held.add(new StoredChat.Held(e.msg, e.seq, f));
        }
        RenderPlan p = s.lastPlan;
        if (p != null) {
            c.hasPlan = true;
            c.planClear = p.clear;
            c.planAlert = p.alert;
            c.planMore = p.moreLine;
            c.planSender = p.moreSenderName;
            c.planMessages.addAll(p.messages);
        }
        return c;
    }

    public static ChatState restore(ChatKey key, StoredChat c) {
        ChatState s = new ChatState(key);
        s.readUpTo = c.readUpTo;
        s.replyReadUpTo = c.replyReadUpTo;
        s.slots = c.slots;
        s.digestCount = c.digestCount;
        s.digestText = c.digestText;
        s.digestSenderId = c.digestSenderId;
        s.digestSenderName = c.digestSenderName;
        s.digestArrival = c.digestArrival;
        s.nextSeq = c.nextSeq;
        for (StoredChat.Held h : c.held) {
            ChatState.Entry e = new ChatState.Entry(h.msg, h.seq);
            e.displayed = (h.flags & StoredChat.F_DISPLAYED) != 0;
            e.cleared = (h.flags & StoredChat.F_CLEARED) != 0;
            e.read = (h.flags & StoredChat.F_READ) != 0;
            e.copyAbsorbed = (h.flags & StoredChat.F_COPY_ABSORBED) != 0;
            s.entries.add(e);
        }
        if (c.hasPlan) {
            List<Message> msgs = new java.util.ArrayList<>(c.planMessages);
            s.lastPlan = new RenderPlan(msgs, c.planMore, c.planSender, c.planAlert, c.planClear);
        }
        return s;
    }

    /**
     * What gets written (R10): at most {@link ChatReducer#MAX_MESSAGES} messages, and when nothing in
     * the chat is unread the message text is dropped (markers, ids and times stay for dedupe).
     */
    public static StoredChat forDisk(StoredChat c) {
        boolean anyUnread = false;
        for (StoredChat.Held h : c.held) if ((h.flags & StoredChat.F_CLEARED) == 0) anyUnread = true;
        if (anyUnread) return capped(c);
        StoredChat o = copyMeta(c);
        int from = Math.max(0, c.held.size() - ChatReducer.MAX_MESSAGES);
        for (StoredChat.Held h : c.held.subList(from, c.held.size())) {
            Message m = h.msg;
            o.held.add(new StoredChat.Held(new Message(m.itemId, m.senderId, m.senderIsViewer, "", m.serverTime,
                    m.arrivalTime, m.edited, m.kind), h.seq, h.flags));
        }
        // a cleared chat has no plan to keep alive
        o.planMessages.clear();
        return o;
    }

    /** As {@link #forDisk(StoredChat)}, but {@code keepTexts} keeps read texts (a debug option). */
    public static StoredChat forDisk(StoredChat c, boolean keepTexts) {
        return keepTexts ? capped(c) : forDisk(c);
    }

    private static StoredChat capped(StoredChat c) {
        if (c.held.size() <= ChatReducer.MAX_MESSAGES) return c;
        StoredChat o = copyMeta(c);
        o.planMessages.addAll(c.planMessages);
        o.held.addAll(c.held.subList(c.held.size() - ChatReducer.MAX_MESSAGES, c.held.size()));
        return o;
    }

    private static StoredChat copyMeta(StoredChat c) {
        StoredChat o = new StoredChat();
        o.readUpTo = c.readUpTo;
        o.replyReadUpTo = c.replyReadUpTo;
        o.slots = c.slots;
        o.digestCount = c.digestCount;
        o.digestText = c.digestText;
        o.digestSenderId = c.digestSenderId;
        o.digestSenderName = c.digestSenderName;
        o.digestArrival = c.digestArrival;
        o.nextSeq = c.nextSeq;
        o.hasPlan = c.hasPlan;
        o.planClear = c.planClear;
        o.planAlert = c.planAlert;
        o.planMore = c.planMore;
        o.planSender = c.planSender;
        return o;
    }

}
