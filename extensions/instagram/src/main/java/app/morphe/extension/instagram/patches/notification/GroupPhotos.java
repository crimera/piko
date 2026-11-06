package app.morphe.extension.instagram.patches.notification;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.patches.notification.state.GroupIconRules;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Icons of group chats that are not among the few chats Instagram draws a share shortcut for:
 * the group's own photo, else two members' photos side by side (what Instagram shows for a group
 * without a photo). Built from the thread Instagram parses (its id, photo and members, handed over
 * by the parser hooks) and only for groups that have been notified, then the chat's shortcut is
 * pushed again. Files sit next to the other chat avatars, so logout removes them.
 */
public final class GroupPhotos {
    private GroupPhotos() {
    }

    private static final String IMAGE_INFO_CLASS = "com.instagram.model.mediasize.ImageInfoImpl";
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60_000L;
    private static final int MAX_THREADS = 200;
    private static final int MAX_MEMBERS = 12;
    private static final long COMPOSITE_RETRY_MS = 5_000;
    // The parser sets a thread's fields one call at a time: wait for it to finish before reading.
    private static final long PARSE_SETTLE_MS = 2_000;

    /** What the parser has handed over so far for one thread object. */
    private static final class Parts {
        String id;
        Object image;
        List<Object> members;
    }

    /** What is known about a thread id. */
    private static final class Info {
        volatile Object image;
        volatile List<Object> members;
    }

    // Thread object being parsed -> its parts; the parser sets one field per call, in any order.
    private static final Map<Object, Parts> parsing = new WeakHashMap<>();
    private static final Map<String, Info> threads = Collections.synchronizedMap(
            new LinkedHashMap<String, Info>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Info> eldest) {
                    return size() > MAX_THREADS;
                }
            });
    // Groups that were notified -> their name, so only those get an icon built.
    private static final Map<String, CharSequence> labels = new ConcurrentHashMap<>();
    private static final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    // Downloads and composite retries tried in this process, so each happens once.
    private static final Set<String> tried = ConcurrentHashMap.newKeySet();
    private static Handler worker;

    // ---- Parser hooks: called on Instagram's parsing thread, so they only record and never throw ----

    public static void onThreadId(Object thread, String id) {
        if (thread == null || TextUtils.isEmpty(id)) return;
        record(thread, p -> p.id = id);
    }

    public static void onThreadImage(Object thread, Object image) {
        if (thread == null || image == null) return;
        record(thread, p -> p.image = image);
    }

    public static void onThreadMembers(Object thread, List<?> users) {
        if (thread == null || users == null || users.isEmpty()) return;
        try {
            List<Object> copy = new ArrayList<>(users.subList(0, Math.min(users.size(), MAX_MEMBERS * 2)));
            record(thread, p -> p.members = copy);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to record a thread's members", ex);
        }
    }

    private interface Change {
        void apply(Parts parts);
    }

    private static void record(Object thread, Change change) {
        try {
            String id;
            synchronized (parsing) {
                Parts parts = parsing.get(thread);
                if (parts == null) {
                    parts = new Parts();
                    parsing.put(thread, parts);
                }
                change.apply(parts);
                id = parts.id;
                if (id == null) return;
                Info info = threads.get(id);
                if (info == null) {
                    info = new Info();
                    threads.put(id, info);
                }
                if (parts.image != null) info.image = parts.image;
                if (parts.members != null) info.members = parts.members;
            }
            if (labels.containsKey(id)) schedule(id, PARSE_SETTLE_MS);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to record a parsed thread", ex);
        }
    }

    // ---- Icons ----

    static File groupFile(Context context, String threadId, String kind) {
        return new File(new File(context.getCacheDir(), ChatShortcuts.AVATAR_DIRECTORY), threadId + "." + kind + ".png");
    }

    /** Called whenever a group is shown: remembers its name and builds its icon if it has none yet. */
    static void onGroupShown(String threadId, CharSequence label, String kind) {
        if (threadId == null || TextUtils.isEmpty(label)) return;
        labels.put(threadId, label);
        // Instagram's own avatar needs nothing; anything else may need building or a weekly refresh.
        if (!"photo".equals(kind)) schedule(threadId, 0);
    }

    private static void schedule(String threadId, long delayMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !scheduled.add(threadId)) return;
        try {
            worker().postDelayed(() -> {
                try {
                    ensure(threadId);
                } catch (Throwable ex) {
                    Logger.printException(() -> "Failed to build a group icon", ex);
                } finally {
                    scheduled.remove(threadId);
                }
            }, delayMs);
        } catch (Throwable ex) {
            scheduled.remove(threadId);
        }
    }

    /**
     * Instagram's own avatar of the chat wins when there is one (it is what Instagram shows), so
     * nothing is downloaded or drawn then. Otherwise the group's photo is downloaded (once a week),
     * and a group without a photo, or with downloads turned off, gets the composite.
     */
    @TargetApi(Build.VERSION_CODES.R)
    private static void ensure(String threadId) throws Exception {
        Info info = threads.get(threadId);
        if (info == null) return; // Learned on the next inbox load or opened chat.
        Context context = Utils.getContext();
        if (ChatShortcuts.avatarFile(context, threadId).isFile()) return;
        File photo = groupFile(context, threadId, "group");
        boolean changed = false;

        String url = imageUrl(info.image);
        if (url != null) {
            if (!fresh(photo) && NotificationImages.enabled() && tried.add("group:" + threadId)) {
                Bitmap source = NotificationImages.downloadBitmap(url, ChatShortcuts.ICON_SIZE);
                if (source != null) {
                    write(photo, circle(source, ChatShortcuts.ICON_SIZE));
                    NotificationLog.event("GROUP PHOTO saved for " + threadId);
                    changed = true;
                } else {
                    NotificationLog.event("FAILED group photo for " + threadId);
                }
            }
        } else if (photo.isFile() && !fresh(photo)) {
            // No photo in this process's parse and ours is a week old: the group removed it.
            if (photo.delete()) {
                NotificationLog.event("GROUP PHOTO removed for " + threadId);
                changed = true;
            }
        }

        if (!photo.isFile()) {
            File composite = groupFile(context, threadId, "composite");
            if (!fresh(composite)) {
                int found = composite(context, info.members, composite);
                if (found >= 2) {
                    NotificationLog.event("GROUP COMPOSITE saved for " + threadId);
                    changed = true;
                } else if (tried.add("composite:" + threadId)) {
                    // Member photos are prefetched as the members are parsed: try again once they are in.
                    // Posted, not scheduled from here, as this task still holds the chat's schedule slot.
                    NotificationLog.event("GROUP COMPOSITE waiting for photos of " + threadId + " (" + found + " of 2)");
                    worker().postDelayed(() -> schedule(threadId, 0), COMPOSITE_RETRY_MS);
                }
            }
        }
        if (changed) republish(context, threadId);
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static void republish(Context context, String threadId) {
        CharSequence label = labels.get(threadId);
        if (TextUtils.isEmpty(label)) return;
        ChatShortcuts.GroupIcon group = ChatShortcuts.savedGroupIconOrLetter(context, threadId, label);
        String shortcutId = ConversationNotifications.SHORTCUT_PREFIX + threadId;
        if (ChatShortcuts.publish(context, shortcutId, threadId, label, group.icon, null, false, true)) {
            NotificationLog.event("SHORTCUT republished " + threadId + " label=" + label + " icon=" + group.kind);
        }
    }

    /** The photo's address: the ImageInfoImpl inside the wrapper the parser stored, then its best candidate. */
    private static String imageUrl(Object image) {
        if (image == null) return null;
        try {
            Object info = image.getClass().getName().equals(IMAGE_INFO_CLASS) ? image : null;
            if (info == null) {
                for (Field field : image.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !field.getType().getName().equals(IMAGE_INFO_CLASS)) continue;
                    field.setAccessible(true);
                    info = field.get(image);
                    break;
                }
            }
            if (info == null) return null;
            for (Field list : info.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(list.getModifiers()) || !List.class.isAssignableFrom(list.getType())) continue;
                list.setAccessible(true);
                String url = bestUrl((List<?>) list.get(info));
                if (url != null) return url;
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a group photo", ex);
        }
        return null;
    }

    /** The candidate {@link GroupIconRules#bestCandidate} picks; candidates are Instagram's ImageUrls. */
    private static String bestUrl(List<?> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        List<String> urls = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            try {
                Object url = candidate.getClass().getMethod("getUrl").invoke(candidate);
                if (!(url instanceof String) || !((String) url).startsWith("http")) continue;
                int width = 0;
                try {
                    Method getWidth = candidate.getClass().getMethod("getWidth");
                    width = ((Number) getWidth.invoke(candidate)).intValue();
                } catch (Throwable ignored) {
                    // Without sizes the first candidate is used.
                }
                urls.add((String) url);
                widths.add(width);
            } catch (Throwable ignored) {
                // Not an image candidate: this list holds something else.
                return null;
            }
        }
        int[] sizes = new int[widths.size()];
        for (int i = 0; i < sizes.length; i++) sizes[i] = widths.get(i);
        int best = GroupIconRules.bestCandidate(sizes, ChatShortcuts.ICON_SIZE);
        return best < 0 ? null : urls.get(best);
    }

    /**
     * Two fixed members (lowest ids with a saved photo, not the viewer), so the icon never flips
     * with each message. Returns how many photos were found; writes the file only with two.
     */
    private static int composite(Context context, List<Object> members, File out) throws Exception {
        if (members == null) return 0;
        List<String> parsed = new ArrayList<>();
        for (Object member : members) {
            try {
                parsed.add(new UserData(member).getUserId());
            } catch (Throwable ignored) {
                // A member without readable details is left out.
            }
        }
        List<String> ids = GroupIconRules.compositeOrder(parsed, ChatMessages::isViewer);
        List<Bitmap> photos = new ArrayList<>();
        for (String id : ids) {
            File file = Avatars.file(context, id);
            Bitmap photo = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
            if (photo != null) photos.add(photo);
            if (photos.size() == 2) break;
        }
        if (photos.size() < 2) return photos.size();

        // Adaptive icons show only their centre disc, about 128 of 192 px, so both circles fit inside it.
        int size = ChatShortcuts.ICON_SIZE;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFFD9DDE2);
        int radius = 35;
        int centre = size / 2;
        drawCircle(canvas, photos.get(1), centre + 20, centre + 20, radius);
        drawCircle(canvas, photos.get(0), centre - 20, centre - 20, radius);
        write(out, bitmap);
        return 2;
    }

    private static void drawCircle(Canvas canvas, Bitmap photo, int cx, int cy, int radius) {
        Bitmap square = centerCrop(photo, radius * 2);
        canvas.save();
        canvas.translate(cx - radius, cy - radius);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawCircle(radius, radius, radius, paint);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3);
        ring.setColor(Color.WHITE);
        canvas.drawCircle(radius, radius, radius - 1.5f, ring);
        canvas.restore();
    }

    private static Bitmap centerCrop(Bitmap source, int size) {
        int side = Math.min(source.getWidth(), source.getHeight());
        Bitmap square = Bitmap.createBitmap(source, (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        return Bitmap.createScaledBitmap(square, size, size, true);
    }

    private static Bitmap circle(Bitmap source, int size) {
        Bitmap square = centerCrop(source, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return bitmap;
    }

    private static boolean fresh(File file) {
        return file.isFile() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS;
    }

    private static void write(File file, Bitmap bitmap) throws Exception {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        if (!tmp.renameTo(file)) tmp.delete();
    }

    /** Called on logout, after the directory was emptied. */
    static void forget() {
        synchronized (parsing) {
            parsing.clear();
        }
        threads.clear();
        labels.clear();
        tried.clear();
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("piko-group-photos");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }
}
