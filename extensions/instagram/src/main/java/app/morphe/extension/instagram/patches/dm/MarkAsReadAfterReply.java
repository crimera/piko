/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import java.util.List;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.MarkChatAsReadScope;

import com.instagram.common.session.UserSession;
import com.instagram.model.direct.DirectThreadKey;

@SuppressWarnings("unused")
public final class MarkAsReadAfterReply {
    private static final PendingReadReceipts PENDING = new PendingReadReceipts();

    private MarkAsReadAfterReply() {
    }

    public static void onSeenRequest(UserSession session, Object context,
                                     String threadId, String messageId, String senderId) {
        if (!Pref.markAsReadAfterReply()) {
            PENDING.clear();
            return;
        }
        if (!Pref.viewDmAnonymously() || session == null || threadId == null
                || messageId == null || senderId == null) return;

        // Keep the receipt Instagram tried to send, including its original message cursor.
        PENDING.remember(session, threadId,
                () -> sendReceipt(session, context, threadId, messageId, senderId));
    }

    public static void onReplySent(UserSession session, List<DirectThreadKey> threads) {
        if (!Pref.markAsReadAfterReply()) {
            PENDING.clear();
            return;
        }
        if (!Pref.viewDmAnonymously() || session == null || threads == null) return;

        for (DirectThreadKey thread : threads) {
            if (thread == null) continue;
            String threadId = getThreadId(thread);
            if (threadId == null) continue;
            MarkChatAsReadScope.Operation receipt = PENDING.take(session, threadId);
            if (receipt == null) continue;
            try {
                // Consume the pending receipt before replaying it; later messages stay anonymous.
                MarkChatAsReadScope.run(receipt);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not mark the replied chat as read", exception);
            }
        }
    }

    private static String getThreadId(DirectThreadKey thread) {
        return null;
    }

    private static void sendReceipt(UserSession session, Object context,
                                    String threadId, String messageId, String senderId) {
    }
}
