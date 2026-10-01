/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.Intent;

import com.instagram.common.session.UserSession;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static app.morphe.extension.instagram.utils.IgStr.str;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;

/** Runtime hooks for "Save deleted messages". Fields are resolved at patch time. */
@SuppressWarnings("unused")
public class SavedMessagesHook {

    private static volatile String sMyUserId;
    private static android.os.HandlerThread sWorkerThread;
    private static android.os.Handler sWorker;

    private static void piko(String message) {
        android.util.Log.e("piko", message);
    }

    /** Opens the all-chat history from Piko settings. */
    public static void openDeletedMessages(Context context) {
        try {
            if (context == null) context = PikoUtils.getContext();
            if (context == null) return;
            Intent intent = new Intent(context, DeletedMessagesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            piko("SavedMessagesHook.openDeletedMessages: " + e);
        }
    }

    /** Harvest participant id-to-username mappings from the thread user list. */
    public static void noteThreadUsers(final java.util.List<?> users) {
        if (users == null || users.isEmpty() || !Pref.saveDeletedMessages()) return;
        final java.util.ArrayList<Object> copy;
        try {
            copy = new java.util.ArrayList<Object>(users);
        } catch (Throwable ignored) {
            return;
        }
        getWorker().post(() -> {
            try {
                PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
                for (Object user : copy) {
                    if (user == null) continue;
                    try {
                        UserData data = new UserData(user);
                        String id = data.getUserId();
                        if (id == null || !id.matches("\\d{6,14}")) continue;
                        String name = data.getUsername();
                        if (name == null || name.isEmpty()) name = data.getFullName();
                        if (name != null && !name.isEmpty()) db.putUsername(id, name);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static String myUserId() {
        if (sMyUserId == null) {
            try {
                Context context = PikoUtils.getContext();
                if (context != null) {
                    String value = context.getSharedPreferences("piko_dm", Context.MODE_PRIVATE)
                            .getString("my_user_id", null);
                    if (value != null && !value.isEmpty()) sMyUserId = value;
                }
            } catch (Exception ignored) {
            }
        }
        return sMyUserId;
    }

    private static void rememberMyUserId(String id) {
        if (id == null || id.isEmpty() || id.equals(sMyUserId)) return;
        sMyUserId = id;
        try {
            Context context = PikoUtils.getContext();
            if (context != null) {
                context.getSharedPreferences("piko_dm", Context.MODE_PRIVATE)
                        .edit().putString("my_user_id", id).apply();
            }
        } catch (Exception ignored) {
        }
    }

    static boolean shouldCaptureReceived(boolean sentByViewer, String senderId, String viewerId) {
        return !sentByViewer && (isBlank(senderId) || !senderId.equals(viewerId));
    }

    static String resolveViewerId(boolean sentByViewer, String senderId,
                                  String sessionViewerId) {
        if (sentByViewer) rememberMyUserId(senderId);
        return !isBlank(sessionViewerId) ? sessionViewerId : myUserId();
    }

    static boolean shouldCaptureStored(String senderId, String viewerId) {
        return senderId == null || viewerId == null || !senderId.equals(viewerId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static synchronized android.os.Handler getWorker() {
        if (sWorker == null) {
            sWorkerThread = new android.os.HandlerThread("piko-dm-hook");
            sWorkerThread.start();
            sWorker = new android.os.Handler(sWorkerThread.getLooper());
        }
        return sWorker;
    }

    /** REST thread-history path. */
    public static void onMessageReceived(final Object item) {
        enqueueReceivedItem(item, null, null);
    }

    /** MQTT path. Parameters match A0P's contiguous p0..p2 registers. */
    public static void onMessageReceived(final Object item, final UserSession userSession,
                                         final Object delta) {
        enqueueReceivedItem(item, userSession, delta);
    }

    private static void enqueueReceivedItem(final Object item, final UserSession userSession,
                                            final Object delta) {
        boolean enabled = Pref.saveDeletedMessages();
        if (item == null || !enabled) return;
        boolean acceptedClass = item.getClass().getName().startsWith("X.");
        if (!acceptedClass) return;
        String sessionUserId = null;
        try {
            sessionUserId = userSession != null ? userSession.getUserId() : null;
            if (!isBlank(sessionUserId)) rememberMyUserId(sessionUserId);
        } catch (Exception ignored) {}
        final String viewerId = sessionUserId;
        getWorker().post(() -> processReceivedItem(
                item, resolveThreadIdHint(delta), viewerId));
    }

    static String resolveThreadIdHint(Object delta) {
        if (delta == null) return null;
        try {
            Field field = delta.getClass().getDeclaredField(deltaThreadIdFieldName());
            if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            return (String) field.get(delta);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String deltaThreadIdFieldName() {
        return "deltaThreadIdField";
    }

    private static void processReceivedItem(Object item, String threadIdHint,
                                            String sessionViewerId) {
        try {
            DirectItem directItem = new DirectItem(item);
            String senderId = directItem.getUserId();
            boolean sentByViewer = directItem.isSentByViewer();
            String viewerId = resolveViewerId(sentByViewer, senderId, sessionViewerId);
            if (!shouldCaptureReceived(sentByViewer, senderId, viewerId)) return;

            String serverId = directItem.getItemId();
            String clientContext = directItem.getClientContext();
            if (isBlank(serverId) && isBlank(clientContext)) return;

            String threadId = directItem.getThreadId();
            if (isBlank(threadId) && !isBlank(threadIdHint)) threadId = threadIdHint;
            if (threadId == null) threadId = "";

            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
            String senderUsername = db.getUsername(senderId);
            if (senderUsername == null) {
                senderUsername = db.getThreadUsername(threadId, senderId);
            }
            String content = directItem.getText();
            String type = directItem.getItemType();
            if (type != null) type = type.trim().toLowerCase(java.util.Locale.ROOT);
            long timestamp = directItem.getTimestampMs();
            boolean hidden = directItem.isHideInThread();

            if ("action_log".equals(type) || "expired_placeholder".equals(type)
                    || "placeholder".equals(type)) {
                return;
            }

            if (type != null && !"text".equals(type)
                    && (isBlank(content) || !content.startsWith("http"))) {
                String url = directItem.getMediaUrl();
                if (url == null && type.startsWith("xma")) {
                    url = directItem.xmaReshareLink();
                }
                if (url != null && url.startsWith("http")) content = url;
            }

            if (hidden) {
                String messageId = db.resolveAndMergeMessageId(serverId, clientContext);
                if (messageId == null) return;
                markDeletedAndNotify(db, messageId);
                String storedContent = db.getStoredContent(messageId);
                antiRevokeItem(directItem,
                        !isBlank(storedContent) ? storedContent : content);
                return;
            }

            db.upsertMessage(serverId, clientContext, threadId, senderId,
                    senderUsername, content, type, timestamp);
        } catch (Exception e) {
            piko("SavedMessagesHook.processReceivedItem: " + e);
        }
    }

    private static void antiRevokeItem(DirectItem directItem, String restoredContent) {
        directItem.setHideInThread(false);
        if (!isBlank(restoredContent)) directItem.setText(restoredContent);
    }

    private static void markDeletedAndNotify(PikoMessageDb db, String messageId) {
        if (!db.markDeleted(messageId)) return;

        String type = db.getMessageType(messageId);
        String content = db.getStoredContent(messageId);
        boolean isMedia = isBlank(content) || content.startsWith("http")
                || content.startsWith("[");
        notifyDeletion(db.getSenderDisplay(messageId),
                isMedia ? describeMediaType(type) : content, type);
    }

    private static void notifyDeletion(String sender, String content, String type) {
        try {
            Context context = PikoUtils.getContext();
            if (context == null) return;

            android.app.NotificationManager manager =
                    (android.app.NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            String channelId = "piko_deleted_messages";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId, str("piko_deleted_messages_channel"),
                        android.app.NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(str("piko_deleted_messages_channel_desc"));
                manager.createNotificationChannel(channel);
            }

            String who = !isBlank(sender) ? sender : str("piko_someone");
            String body = !isBlank(content)
                    ? content
                    : !isBlank(type) ? "[" + type + "]" : str("piko_media_deleted_generic");

            Intent intent = new Intent(context, DeletedMessagesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int pendingIntentFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    | (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                    ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    context, 0, intent, pendingIntentFlags);

            int icon = context.getApplicationInfo().icon;
            android.app.Notification.Builder builder =
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                            ? new android.app.Notification.Builder(context, channelId)
                            : new android.app.Notification.Builder(context);
            android.app.Notification notification = builder
                    .setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_dialog_info)
                    .setContentTitle(String.format(str("piko_deleted_a_message"), who))
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build();

            manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
        } catch (Exception e) {
            piko("SavedMessagesHook.notifyDeletion: " + e);
        }
    }

    private static String describeMediaType(String type) {
        if (type == null) return str("piko_media_deleted_generic");
        String label;
        switch (type) {
            case "media":
            case "image":           label = str("piko_media_photo"); break;
            case "raven_media":     label = str("piko_media_disappearing_photo"); break;
            case "video":           label = str("piko_media_video"); break;
            case "voice_media":
            case "audio":           label = str("piko_media_voice"); break;
            case "animated_media":  label = str("piko_media_gif"); break;
            case "reel_share":      label = str("piko_media_reel"); break;
            case "story_share":     label = str("piko_media_story"); break;
            case "media_share":     label = str("piko_media_post"); break;
            case "like":            label = str("piko_media_like"); break;
            case "link":            label = str("piko_media_link"); break;
            case "action_log":      label = str("piko_media_activity"); break;
            default:                 label = type; break;
        }
        return String.format(str("piko_media_deleted"), label);
    }

    public static void onMessageHiddenFromDb(String serverId, String clientContext) {
        if (!Pref.saveDeletedMessages()) return;
        if (isBlank(serverId) && isBlank(clientContext)) return;
        getWorker().post(() -> processMessageHiddenFromDb(serverId, clientContext));
    }

    private static void processMessageHiddenFromDb(String serverId, String clientContext) {
        try {
            if (!Pref.saveDeletedMessages()) return;
            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
            String messageId = db.resolveAndMergeMessageId(serverId, clientContext);
            if (messageId == null) return;
            if (!shouldCaptureStored(db.getSenderId(messageId), myUserId())) return;
            markDeletedAndNotify(db, messageId);
        } catch (Exception e) {
            piko("SavedMessagesHook.processMessageHiddenFromDb: " + e);
        }
    }
}
