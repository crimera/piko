/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** The actions of a chat notification: reply semantics, Android Auto's mark as read, and swipes. */
final class ChatActions {
    private ChatActions() {
    }

    // androidx keeps invisible actions inside the car extender, which is where Android Auto reads them.
    static final String CAR_EXTENDER = "android.car.EXTENSIONS";

    static final String INVISIBLE_ACTIONS = "invisible_actions";

    static final String SHOWS_USER_INTERFACE = "android.support.action.showsUserInterface";

    /** Chains the delete intent Instagram had, which the swipe would otherwise no longer reach. */
    static PendingIntent dismissIntent(Context context, String tag, int id, PendingIntent original) {
        Intent intent = new Intent(context, NotificationDismissedReceiver.class)
                .setIdentifier(tag + "#" + id + "#dismissed")
                .putExtra(NotificationDismissedReceiver.EXTRA_TAG, tag)
                .putExtra(NotificationDismissedReceiver.EXTRA_ID, id);
        if (original != null) intent.putExtra(NotificationDismissedReceiver.EXTRA_ORIGINAL, original);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Android Auto only offers to reply when the reply action says it is one. */
    @TargetApi(Build.VERSION_CODES.R)
    static Notification.Action[] withReplySemantics(Notification.Action[] actions) {
        // androidx reads NotificationCompat.Action#getShowsUserInterface from this extra.
        Bundle noUserInterface = new Bundle();
        noUserInterface.putBoolean(SHOWS_USER_INTERFACE, false);

        Notification.Action[] result = new Notification.Action[actions.length];
        for (int i = 0; i < actions.length; i++) {
            Notification.Action action = actions[i];
            boolean isReply = action.getRemoteInputs() != null && action.getRemoteInputs().length > 0;
            result[i] = isReply && action.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_NONE
                    ? new Notification.Action.Builder(action)
                            .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                            .addExtras(noUserInterface)
                            .build()
                    : action;
        }
        return result;
    }

    /**
     * Android Auto also expects a mark as read action. It is added as an invisible action, in the
     * same bundle format androidx's NotificationCompat.Builder.addInvisibleAction writes, so the
     * notification in the shade keeps its own buttons.
     */
    @TargetApi(Build.VERSION_CODES.R)
    static void addMarkAsReadAction(Context context, Notification notification, String tag, int id) {
        Intent intent = new Intent(context, MarkAsReadReceiver.class)
                .setIdentifier(tag + "#" + id)
                .putExtra(MarkAsReadReceiver.EXTRA_TAG, tag)
                .putExtra(MarkAsReadReceiver.EXTRA_ID, id);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Bundle action = new Bundle();
        action.putInt("icon", context.getApplicationInfo().icon);
        action.putCharSequence("title", Texts.markAsRead());
        action.putParcelable("actionIntent", pendingIntent);
        action.putBundle("extras", new Bundle());
        action.putInt("semanticAction", Notification.Action.SEMANTIC_ACTION_MARK_AS_READ);
        action.putBoolean("showsUserInterface", false);

        Bundle invisibleActions = new Bundle();
        invisibleActions.putBundle("0", action);

        Bundle extras = notification.extras;
        Bundle carExtender = extras.getBundle(CAR_EXTENDER);
        carExtender = carExtender == null ? new Bundle() : new Bundle(carExtender);
        carExtender.putBundle(INVISIBLE_ACTIONS, invisibleActions);
        extras.putBundle(CAR_EXTENDER, carExtender);
    }
}
