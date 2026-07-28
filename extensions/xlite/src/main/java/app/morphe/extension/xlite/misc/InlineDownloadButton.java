package app.morphe.extension.xlite.misc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.x.models.InlineActionEntry;
import com.x.models.PostActionType;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.xlite.settings.SettingsRegistry;
import kotlin.jvm.functions.Function1;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "xlite.content.inline_download_button";
    private static final String CARRIER_ACTION_NAME = "TwitterShare";
    private static final String CONTEXTUAL_POST_CLASS = "com.x.models.ContextualPost";
    private static final int MAX_TRACKED_OBJECTS = 128;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final List<WeakReference<InlineActionEntry>> DOWNLOAD_ACTIONS = new ArrayList<>();
    private static final Map<String, WeakReference<Function1<Object, Object>>> EVENT_SINKS =
            new HashMap<>();
    private static volatile Constructor<?> downloadEventConstructor;

    private InlineDownloadButton() {
    }

    public static List<?> addAction(List<?> actions) {
        if (!isEnabled() || actions == null) return actions;
        if (containsDownloadAction(actions)) return actions;

        try {
            InlineActionEntry downloadAction = createDownloadAction();
            registerDownloadAction(downloadAction);

            List<Object> result = new ArrayList<>(actions.size() + 1);
            result.addAll(actions);
            result.add(downloadAction);
            return result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to add the X-Lite inline download action", exception);
            return actions;
        }
    }

    public static float markIconSize(InlineActionEntry action, float iconSize) {
        if (!isDownloadAction(action)) return iconSize;
        return -Math.abs(iconSize);
    }

    public static Object selectIcon(Object nativeIcon, float markedIconSize, Object downloadIcon) {
        return isMarkedIconSize(markedIconSize) ? downloadIcon : nativeIcon;
    }

    public static String contentDescription(String nativeDescription, float markedIconSize) {
        return isMarkedIconSize(markedIconSize) ? "Download" : nativeDescription;
    }

    public static float normalizeIconSize(float markedIconSize) {
        return Math.abs(markedIconSize);
    }

    @SuppressWarnings("unchecked")
    public static void registerEventSink(Object postIdentifier, Object eventSink) {
        if (!(eventSink instanceof Function1<?, ?>)) return;

        String postId = identifierValue(postIdentifier);
        if (postId == null) return;

        synchronized (EVENT_SINKS) {
            removeClearedEventSinks();
            if (EVENT_SINKS.size() >= MAX_TRACKED_OBJECTS && !EVENT_SINKS.containsKey(postId)) {
                EVENT_SINKS.clear();
            }
            EVENT_SINKS.put(
                    postId,
                    new WeakReference<>((Function1<Object, Object>) eventSink)
            );
        }
    }

    public static boolean handleEvent(Object presenter, Object event) {
        InlineActionEntry action = findActionEntry(event);
        if (!isDownloadAction(action)) return false;

        try {
            PresenterData presenterData = findPresenterData(presenter);
            if (presenterData.context == null || presenterData.post == null) {
                showToast(presenterData.context, "Could not find the selected post");
                return true;
            }

            List<?> media = mediaFor(presenterData.post);
            if (media.isEmpty()) {
                showToast(presenterData.context, "No media to download");
                return true;
            }

            String postId = postId(presenterData.post);
            Function1<Object, Object> eventSink = eventSink(postId);
            if (eventSink == null) {
                showToast(presenterData.context, "Post download action is no longer available");
                return true;
            }

            boolean downloadAll = event != null &&
                    event.toString().startsWith("DidLongClickInlineActionEntry");
            if (downloadAll || media.size() == 1) {
                emitDownloads(presenterData.context, eventSink, media, downloadAll);
                return true;
            }

            showMediaPicker(presenterData.context, eventSink, media);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to handle the X-Lite inline download action", exception);
            showToast(null, "Could not download post media");
            return true;
        }
    }

    private static boolean isEnabled() {
        try {
            return SettingsRegistry.getBoolean(SETTING_ID);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static InlineActionEntry createDownloadAction() throws ReflectiveOperationException {
        Class actionClass = PostActionType.class;
        Object carrierAction = Enum.valueOf(actionClass, CARRIER_ACTION_NAME);
        Constructor<InlineActionEntry> constructor = InlineActionEntry.class.getDeclaredConstructor(
                PostActionType.class,
                Long.class,
                boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(carrierAction, null, true);
    }

    private static boolean containsDownloadAction(List<?> actions) {
        for (Object action : actions) {
            if (action instanceof InlineActionEntry entry && isDownloadAction(entry)) return true;
        }
        return false;
    }

    private static void registerDownloadAction(InlineActionEntry action) {
        synchronized (DOWNLOAD_ACTIONS) {
            removeClearedDownloadActions();
            if (DOWNLOAD_ACTIONS.size() >= MAX_TRACKED_OBJECTS) DOWNLOAD_ACTIONS.clear();
            DOWNLOAD_ACTIONS.add(new WeakReference<>(action));
        }
    }

    private static boolean isDownloadAction(InlineActionEntry candidate) {
        if (candidate == null) return false;

        synchronized (DOWNLOAD_ACTIONS) {
            Iterator<WeakReference<InlineActionEntry>> iterator = DOWNLOAD_ACTIONS.iterator();
            while (iterator.hasNext()) {
                InlineActionEntry action = iterator.next().get();
                if (action == null) {
                    iterator.remove();
                    continue;
                }
                if (action == candidate) return true;
            }
        }
        return false;
    }

    private static void removeClearedDownloadActions() {
        DOWNLOAD_ACTIONS.removeIf(reference -> reference.get() == null);
    }

    private static boolean isMarkedIconSize(float iconSize) {
        return Float.floatToRawIntBits(iconSize) < 0;
    }

    private static InlineActionEntry findActionEntry(Object event) {
        if (event == null) return null;

        try {
            for (Field field : event.getClass().getDeclaredFields()) {
                if (field.getType() != InlineActionEntry.class) continue;
                field.setAccessible(true);
                return (InlineActionEntry) field.get(event);
            }
        } catch (IllegalAccessException exception) {
            Logger.printException(() -> "Failed to read the X-Lite inline action event", exception);
        }
        return null;
    }

    private static PresenterData findPresenterData(Object presenter) throws IllegalAccessException {
        if (presenter == null) return new PresenterData(null, null);

        Context context = null;
        Object post = null;
        for (Field field : presenter.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (Context.class.isAssignableFrom(field.getType())) context = (Context) field.get(presenter);
            if (CONTEXTUAL_POST_CLASS.equals(field.getType().getName())) post = field.get(presenter);
        }
        return new PresenterData(context, post);
    }

    private static List<?> mediaFor(Object post) throws ReflectiveOperationException {
        Object media = invoke(post, "getMedia");
        return media instanceof List<?> list ? list : java.util.Collections.emptyList();
    }

    private static String postId(Object post) throws ReflectiveOperationException {
        return identifierValue(invoke(post, "getId"));
    }

    private static String identifierValue(Object identifier) {
        if (identifier == null) return null;

        try {
            Object value = invoke(identifier, "getValue");
            String id = value == null ? null : String.valueOf(value).trim();
            if (id != null && !id.isEmpty()) return id;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object value = invoke(identifier, "getStr");
            String id = value == null ? null : String.valueOf(value).trim();
            return id == null || id.isEmpty() ? null : id;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Function1<Object, Object> eventSink(String postId) {
        if (postId == null) return null;

        synchronized (EVENT_SINKS) {
            WeakReference<Function1<Object, Object>> reference = EVENT_SINKS.get(postId);
            Function1<Object, Object> sink = reference == null ? null : reference.get();
            if (reference != null && sink == null) EVENT_SINKS.remove(postId);
            return sink;
        }
    }

    private static void removeClearedEventSinks() {
        EVENT_SINKS.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    private static void showMediaPicker(
            Context context,
            Function1<Object, Object> eventSink,
            List<?> media
    ) {
        if (!(context instanceof Activity activity)) {
            emitDownload(context, eventSink, media.get(0));
            return;
        }

        String[] labels = new String[media.size() + 1];
        for (int index = 0; index < media.size(); index++) {
            labels[index] = mediaLabel(media.get(index), index);
        }
        labels[media.size()] = "Download all";

        MAIN_HANDLER.post(() -> new AlertDialog.Builder(activity)
                .setTitle("Download")
                .setItems(labels, (dialog, selectedIndex) -> {
                    if (selectedIndex == media.size()) {
                        emitDownloads(context, eventSink, media, true);
                        return;
                    }
                    emitDownload(context, eventSink, media.get(selectedIndex));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private static String mediaLabel(Object media, int index) {
        if (media == null) return "Media " + (index + 1);
        String simpleName = media.getClass().getSimpleName();
        if (simpleName.endsWith("Image")) return "Image " + (index + 1);
        if (simpleName.endsWith("Gif")) return "GIF " + (index + 1);
        if (simpleName.endsWith("Video")) return "Video " + (index + 1);
        return "Media " + (index + 1);
    }

    private static void emitDownloads(
            Context context,
            Function1<Object, Object> eventSink,
            List<?> media,
            boolean downloadAll
    ) {
        int count = downloadAll ? media.size() : 1;
        for (int index = 0; index < count; index++) {
            emitDownload(context, eventSink, media.get(index));
        }
    }

    private static void emitDownload(
            Context context,
            Function1<Object, Object> eventSink,
            Object media
    ) {
        if (media == null) return;

        Runnable emit = () -> {
            try {
                eventSink.invoke(newDownloadEvent(media));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Logger.printException(() -> "Failed to emit the X-Lite media download event", exception);
                showToast(context, "Could not download post media");
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            emit.run();
            return;
        }
        MAIN_HANDLER.post(emit);
    }

    private static Object newDownloadEvent(Object media) throws ReflectiveOperationException {
        Constructor<?> constructor = downloadEventConstructor;
        if (constructor == null || !constructor.getParameterTypes()[0].isInstance(media)) {
            ClassLoader classLoader = media.getClass().getClassLoader();
            Class<?> eventClass = Class.forName(getDownloadEventClassName(), false, classLoader);
            constructor = null;
            for (Constructor<?> candidate : eventClass.getDeclaredConstructors()) {
                if (candidate.getParameterCount() != 1) continue;
                if (!candidate.getParameterTypes()[0].isInstance(media)) continue;
                candidate.setAccessible(true);
                constructor = candidate;
                break;
            }
            if (constructor == null) {
                throw new NoSuchMethodException("X-Lite download event constructor not found");
            }
            downloadEventConstructor = constructor;
        }
        return constructor.newInstance(media);
    }

    private static String getDownloadEventClassName() {
        return "com.x.urt.items.post.DownloadMediaRequested";
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void showToast(Context context, String message) {
        if (context == null) return;
        MAIN_HANDLER.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private static final class PresenterData {
        final Context context;
        final Object post;

        PresenterData(Context context, Object post) {
            this.context = context;
            this.post = post;
        }
    }
}
