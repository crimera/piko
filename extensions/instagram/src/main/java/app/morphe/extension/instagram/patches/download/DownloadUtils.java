/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.VideoData;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.AudioMediaInterface;
import app.morphe.extension.instagram.entity.MediaInterface;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaDownloader;
import app.morphe.extension.crimera.downloader.DownloadMetadata;
import app.morphe.extension.crimera.downloader.DownloadRequest;
import app.morphe.extension.crimera.downloader.MediaType;
import app.morphe.extension.crimera.PikoUtils;

import com.instagram.common.session.UserSession;

public class DownloadUtils {

    public static String getSubfolderName(String username){
        boolean SPLIT_BY_USERNAME = Pref.downloadUsernameFolder() && SettingsStatus.downloadMedia;
        return SPLIT_BY_USERNAME ? username : null;
    }

    private static void buildVariantDialogBox(
            Context context,
            MediaData mediaInfo,
            int position,
            MediaType mediaType
    ) throws Exception {
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        String username = mediaInfo.getUserData().getUsername();
        List<MediaInterface> variantList;
        String title = "";
        if(mediaType.equals(MediaType.VIDEO)){
            title = str("piko_video_variants");
            variantList = currentMediaData.getVideoVariants();
        }else{
            title = str("piko_image_variants");
            variantList = currentMediaData.getImageVariants();
        }

        InstagramDialogBox dialog = new InstagramDialogBox(context);
        ArrayList<String> options = new ArrayList<>();
        variantList.forEach(item -> options.add(item.getVariantTag()));
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                MediaInterface data = variantList.get(which);

                try {
                    String subFolder = getSubfolderName(username);
                    String fileName = username + "_" + currentMediaData.getVariantFileName(data);
                    DownloadRequest request = buildMediaRequest(
                            mediaInfo,
                            currentMediaData,
                            position,
                            data.getMediaType(),
                            data.getUrl(),
                            subFolder,
                            fileName,
                            data.getVariantTag()
                    );
                    enqueueDownload(context, request);
                } catch (Exception e) {
                    PikoUtils.logger(e);
                    Logger.printException(() -> "Error at buildVariantDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }

            }
        });

        dialog.setTitle(title);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();

    }

    private static void downloadDialogBox(Context context, MediaData mediaInfo, int position) throws Exception {
        int carouselSize = mediaInfo.getCarouselSize();
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        String username = mediaInfo.getUserData().getUsername();
        Boolean isCurrentMediaVideo = currentMediaData.isVideo();
        Boolean currentMediaHasAudio = currentMediaData.hasAudio();

        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(str("piko_download_current_media"));
        options.add(str("piko_download_as_image"));
        if (currentMediaHasAudio) options.add(str("piko_download_audio"));
        options.add(str("piko_copy_media_link"));
        options.add(str("piko_image_variants"));
        if (isCurrentMediaVideo) {
            options.add(str("piko_video_variants"));
            options.add(str("piko_open_video_externally"));
        } else {
            options.add(str("piko_open_image_externally"));
        }

        if (carouselSize > 1) options.add(str("piko_download_all"));

        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(str("piko_download_current_media"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.ANY);

                    } else if (selectedOption.equals(str("piko_download_as_image"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.IMAGE);

                    } else if (selectedOption.equals(str("piko_copy_media_link"))) {
                        Utils.setClipboard(currentMediaData.getMediaLink());
                        Utils.showToastShort(str("piko_copied_media_link"));

                    } else if (selectedOption.equals(str("piko_open_video_externally")) || selectedOption.equals(str("piko_open_image_externally"))) {
                        ActivityHook.handleUrlIntent(isCurrentMediaVideo, currentMediaData.getMediaLink());

                    } else if (selectedOption.equals(str("piko_download_all"))) {
                        downloadMedia(context, mediaInfo, -1, MediaType.ANY);

                    } else if (selectedOption.equals(str("piko_download_audio"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.AUDIO);

                    } else if (selectedOption.equals(str("piko_video_variants"))) {
                        buildVariantDialogBox(context, mediaInfo, position, MediaType.VIDEO);

                    } else if (selectedOption.equals(str("piko_image_variants"))) {
                        buildVariantDialogBox(context, mediaInfo, position, MediaType.IMAGE);

                    }
                } catch (Exception e) {
                    PikoUtils.logger(e);
                    Logger.printException(() -> "Error at downloadDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });


        dialog.setTitle(str("piko_download_options"));
        dialog.setNegativeButton(str("piko_close"), (d, which) -> d.dismiss());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }


    public static void downloadPost(Context context,  UserSession userSession, Object mediaObject, int position) {
        try {
            boolean ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
            position = position < 1 ? 0 : position;
            MediaData mediaInfo = new MediaData(mediaObject, userSession);
            if (ENABLE_DIRECT_DOWNLOAD) {
                downloadMedia(context, mediaInfo, position, MediaType.ANY);
            } else {
                downloadDialogBox(context, mediaInfo, position);
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
            Logger.printException(() -> "Error at downloadPost", e);
        }
    }

    // Position is set to -1 if we want to download all medias from the media info object.
    public static void downloadMedia(Context context, MediaData mediaInfo, int position, MediaType mediaType) throws Exception {
        if(!Utils.isNetworkConnected()){
            Utils.showToastShort(str("piko_no_internet"));
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        String username = mediaInfo.getUserData().getUsername();
        String subFolder = getSubfolderName(username);

        if (mediaType.equals(MediaType.AUDIO)) {
            AudioMediaInterface audioMedia = mediaInfo.getMediaAt(position).getAudioMedia();
            String audioUrl = audioMedia.getAudioUrl();
            String fileName = audioMedia.getDownloadName() + ".mp3";
            downloader.enqueue(new DownloadRequest(audioUrl, Constants.DEFAULT_AUDIO_FOLDER, fileName));

        } else if (position != -1) {
            MediaData mediaData = mediaInfo.getMediaAt(position);
            String mediaUrl;
            if (mediaType.equals(MediaType.IMAGE)) {
                mediaUrl = mediaData.getImageLink();
            } else {
                mediaUrl = mediaData.getMediaLink();
            }
            String fileName = username + "_" + mediaData.getDownloadFilename(mediaType);
            downloader.enqueue(buildMediaRequest(
                    mediaInfo,
                    mediaData,
                    position,
                    mediaType,
                    mediaUrl,
                    subFolder,
                    fileName,
                    null
            ));

        } else if (position == -1) {
            int carouselSize = mediaInfo.getCarouselSize();

            for (int index = 0; index < carouselSize; index++) {
                MediaData currentMediaData = mediaInfo.getMediaAt(index);
                String fileName = username + "_"
                        + currentMediaData.getDownloadFilename(MediaType.ANY);
                String mediaUrl = currentMediaData.getMediaLink();
                downloader.enqueue(buildMediaRequest(
                        mediaInfo,
                        currentMediaData,
                        index,
                        MediaType.ANY,
                        mediaUrl,
                        subFolder,
                        fileName,
                        null
                ));
            }
        } else {
            Utils.showToastShort("There is nothing to download");
        }

    }

    private static DownloadRequest buildMediaRequest(
            MediaData rootMediaData,
            MediaData childMediaData,
            int carouselIndex,
            MediaType mediaType,
            String mediaUrl,
            String subFolder,
            String fallbackFileName,
            String variantTag
    ) throws Exception {
        boolean isVideo = mediaType.equals(MediaType.VIDEO)
                || (mediaType.equals(MediaType.ANY) && childMediaData.isVideo());
        String fileName = fallbackFileName;

        try {
            String username = rootMediaData.getUserData().getUsername();
            Long takenAtSeconds = rootMediaData.getTakenAtSeconds();
            Long uploadTimestampMillis = takenAtSeconds == null
                    ? null
                    : takenAtSeconds * 1000L;
            String variantSuffix = variantTag == null || variantTag.trim().isEmpty()
                    ? ""
                    : variantTag;
            DownloadFileNameFormatter.Values fileNameValues =
                    new DownloadFileNameFormatter.Values(
                            username,
                            childMediaData.getMediaPkId(),
                            rootMediaData.getShortcode(),
                            uploadTimestampMillis,
                            isVideo ? "video" : "image",
                            carouselIndex,
                            variantSuffix
                    );
            fileName = DownloadFileNameFormatter.format(
                    Pref.downloadFileNameTemplate(),
                    fileNameValues,
                    isVideo ? ".mp4" : ".jpg",
                    ZoneId.systemDefault()
            );
        } catch (Exception | LinkageError fileNameException) {
            PikoUtils.logger(fileNameException);
            Logger.printException(
                    () -> "Could not format download filename",
                    fileNameException
            );
        }

        DownloadRequest request = new DownloadRequest(mediaUrl, subFolder, fileName);
        if (!isVideo || !Pref.embedDownloadMetadata()) {
            return request;
        }

        try {
            String username = rootMediaData.getUserData().getUsername();
            Long takenAtSeconds = rootMediaData.getTakenAtSeconds();
            Long uploadTimestampMillis = takenAtSeconds == null
                    ? null
                    : takenAtSeconds * 1000L;
            DownloadMetadata metadata = new DownloadMetadata(
                    rootMediaData.getDescriptionText(),
                    Links.generatePostLink(rootMediaData, carouselIndex),
                    username,
                    uploadTimestampMillis
            );
            return new DownloadRequest(mediaUrl, subFolder, fileName, metadata);
        } catch (Exception | LinkageError metadataException) {
            PikoUtils.logger(metadataException);
            Logger.printException(
                    () -> "Could not collect download metadata",
                    metadataException
            );
            return request;
        }
    }


    public static void downloadMediaUrl(Context context, String mediaUrl, String subFolder, String fileName) throws Exception {
        enqueueDownload(context, new DownloadRequest(mediaUrl, subFolder, fileName));
    }

    private static final Object FEED_DOWNLOAD_BUTTON_TAG = new Object();
    private static final String[] FEED_BUTTON_GROUP_IDS = {
            "row_feed_view_group_social_ufi_buttons",
            "row_feed_view_group_buttons",
    };
    private static final Set<String> feedDownloadButtonLogs = new HashSet<>();
    private static int parentRowFeedButtonSaveId;

    /** Shared by the patch-time Litho component gate and the runtime view holder hook. */
    public static boolean isFeedDownloadButtonEnabled() {
        return Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ENABLE_DOWNLOAD))
                && Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.FEED_DOWNLOAD_BUTTON));
    }

    /**
     * Adds a download button immediately beside the save/bookmark button of a feed post row.
     * Called from the patched feed UFI row binder, so every rebind refreshes the captured media.
     */
    public static void addFeedDownloadButton(View rootView, Object mediaObject, UserSession userSession) {
        try {
            Object media = extractMedia(mediaObject);
            attachFeedDownloadButton(rootView, media == null ? mediaObject : media, userSession);
        } catch (Exception e) {
            Logger.printException(() -> "addFeedDownloadButton failure", e);
        }
    }

    /**
     * @return true once the row's save button (and therefore the download button decision) was
     * resolved, including when the toggle is off; false while the row is still mounting.
     */
    private static boolean attachFeedDownloadButton(
            View rootView, Object mediaObject, UserSession userSession) {
        try {
            if (rootView == null || mediaObject == null) return false;
            Context context = rootView.getContext();
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            String caller = "?";
            for (StackTraceElement element : stack) {
                String name = element.getClassName();
                if (name.startsWith("X.") && !name.contains("DownloadUtils")) {
                    caller = name + "." + element.getMethodName();
                    break;
                }
            }
            logFeedDownloadButton(
                    context,
                    "hook fired via " + caller + " ctx=" + context.getClass().getName()
                            + " row=" + rootView.getClass().getName()
                            + " media=" + mediaObject.getClass().getName());
            // The patch is opt-in, so read the download toggles directly instead of the
            // settings-status-gated Pref helper.
            if (!isFeedDownloadButtonEnabled()) {
                logFeedDownloadButton(context, "disabled by settings");
                removeFeedDownloadButton(rootView);
                return true;
            }

            int saveButtonId = ResourceUtils.getIdentifier(context, ResourceType.ID, "row_feed_button_save");
            if (saveButtonId == 0) {
                logFeedDownloadButton(context, "row_feed_button_save id unresolved");
                return false;
            }

            View saveButton = findSaveButton(rootView, saveButtonId);
            if (saveButton == null) {
                logFeedDownloadButton(
                        context,
                        "save button missing in row row=" + rootView.getClass().getName()
                                + " id=" + saveButtonId
                                + " children=" + countViews(rootView, 0)
                                + " ids=" + collectViewIds(rootView));
                return false;
            }

            ViewGroup buttonGroup = resolveFeedButtonGroup(rootView, saveButton);
            if (buttonGroup == null) {
                logFeedDownloadButton(context, "no UFI button group");
                return false;
            }

            ImageView button = buttonGroup.findViewWithTag(FEED_DOWNLOAD_BUTTON_TAG);
            if (button == null) {
                button = createFeedDownloadButton(context, saveButton, buttonGroup);
                logFeedDownloadButton(context, "attached");
            }
            if (button == null) return false;

            button.setOnClickListener(v -> downloadPost(context, userSession, mediaObject, 0));
            return true;
        } catch (Exception e) {
            Logger.printException(() -> "addFeedDownloadButton failure", e);
            return true;
        }
    }

    static Object extractMedia(Object mediaObject) {
        if (mediaObject == null) return null;
        if ("com.instagram.feed.media.Media".equals(mediaObject.getClass().getName())) return mediaObject;
        try {
            for (Method method : mediaObject.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() != 0) continue;
                if (!"com.instagram.feed.media.Media".equals(method.getReturnType().getName())) continue;
                method.setAccessible(true);
                Object result = method.invoke(mediaObject);
                if (result != null) return result;
            }
        } catch (Exception ignored) {
        }
        try {
            Object found = null;
            for (Field field : mediaObject.getClass().getDeclaredFields()) {
                if (!"com.instagram.feed.media.Media".equals(field.getType().getName())) continue;
                field.setAccessible(true);
                Object value = field.get(mediaObject);
                if (value == null) continue;
                if (found != null) return mediaObject;
                found = value;
            }
            if (found != null) return found;
        } catch (Exception ignored) {
        }
        return mediaObject;
    }

    /** Counts the views in a subtree (capped): 0 means an unmounted/detached container. */
    private static int countViews(View view, int depth) {
        if (view == null || depth > 8) return 0;
        if (!(view instanceof ViewGroup)) return 1;
        ViewGroup group = (ViewGroup) view;
        int total = 1;
        int children = group.getChildCount();
        for (int i = 0; i < children && total < 500; i++) {
            total += countViews(group.getChildAt(i), depth + 1);
        }
        return total;
    }

    /** Lists `package:id/name` entries in a subtree (capped): shows what IS mounted. */
    private static String collectViewIds(View view) {
        StringBuilder ids = new StringBuilder();
        collectViewIdsInto(view, 0, ids);
        return ids.toString();
    }

    private static void collectViewIdsInto(View view, int depth, StringBuilder ids) {
        if (view == null || depth > 6 || ids.length() > 600) return;
        try {
            int id = view.getId();
            if (id != View.NO_ID) {
                if (ids.length() > 0) ids.append(',');
                try {
                    ids.append(view.getResources().getResourceName(id));
                } catch (Exception ignored) {
                    ids.append("#").append(Integer.toHexString(id));
                }
            }
        } catch (Exception ignored) {
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount() && ids.length() <= 600; i++) {
            collectViewIdsInto(group.getChildAt(i), depth + 1, ids);
        }
    }

    /**
     * Finds the row's save button. Contextual (Litho) rows mount the UFI component as a child of
     * the row container while the bind callback receives a leaf component view (media, header,
     * footer) as its root. Search the root's subtree first, then walk up a bounded number of
     * ancestors and take the first ancestor that contains exactly one save button; an ancestor
     * with several belongs to the list, not to one row. When the UFI component subtree is known
     * from the Litho props, ids are re-verified against the APK's resource ids, because cached
     * ids from a different resource package silently miss.
     */
    private static View findSaveButton(View rootView, int saveButtonId) {
        int verifiedSaveButtonId = verifyViewId(rootView.getContext(), saveButtonId);
        return findDescendantBySaveId(rootView, verifiedSaveButtonId, saveButtonId);
    }

    private static View findDescendantBySaveId(View rootView, int verifiedSaveButtonId, int saveButtonId) {
        if (verifiedSaveButtonId != 0) {
            try {
                View found = rootView.findViewById(verifiedSaveButtonId);
                if (found != null) return found;
            } catch (Exception ignored) {
            }
        }
        if (saveButtonId != 0 && saveButtonId != verifiedSaveButtonId) {
            try {
                return rootView.findViewById(saveButtonId);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Re-reads `row_feed_button_save` against the current resources. Instagram's release id
     * mapping cannot be assumed stable across installs, so a stale cached id is refreshed
     * instead of silently returning a missing save button.
     */
    private static int verifyViewId(Context context, int saveButtonId) {
        try {
            parentRowFeedButtonSaveId = ResourceUtils.getIdentifier(context, ResourceType.ID, "row_feed_button_save");
            return parentRowFeedButtonSaveId;
        } catch (Exception ignored) {
            return saveButtonId;
        }
    }

    /** Drops the button on rebind so turning the toggle off takes effect without recreating the row. */
    private static void removeFeedDownloadButton(View rootView) {
        View existing = rootView.findViewWithTag(FEED_DOWNLOAD_BUTTON_TAG);
        if (!(existing instanceof ImageView)) return;
        ViewParent parent = existing.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(existing);
    }

    /**
     * Resolves the horizontal button row that hosts the save button. The save button's parent is the
     * only correct insert target: `row_feed_view_group_buttons` is an outer frame around the whole
     * UFI area on current releases, while `row_feed_view_group_social_ufi_buttons` is the row itself.
     * Litho component hosts reject manual child views; their download button is injected into the
     * component builder at patch time instead. The id lookups remain as a fallback for layouts
     * where the parent is not a plain ViewGroup.
     */
    private static ViewGroup resolveFeedButtonGroup(View rootView, View saveButton) {
        ViewParent parent = saveButton.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group.getClass().getName().startsWith("com.facebook.litho.")) return null;
            return group;
        }

        for (String idName : FEED_BUTTON_GROUP_IDS) {
            int buttonGroupId = ResourceUtils.getIdentifier(rootView.getContext(), ResourceType.ID, idName);
            if (buttonGroupId == 0) continue;
            View candidate = rootView.findViewById(buttonGroupId);
            if (candidate instanceof ViewGroup) return (ViewGroup) candidate;
        }
        return null;
    }

    private static ImageView createFeedDownloadButton(Context context, View saveButton, ViewGroup buttonGroup) {
        ImageView button = new ImageView(context);
        button.setTag(FEED_DOWNLOAD_BUTTON_TAG);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        applyFeedDownloadIcon(button, context);
        button.setPadding(
                saveButton.getPaddingLeft(),
                saveButton.getPaddingTop(),
                saveButton.getPaddingRight(),
                saveButton.getPaddingBottom());

        int insertIndex = buttonGroup.indexOfChild(saveButton);
        if (insertIndex < 0) insertIndex = buttonGroup.getChildCount();
        buttonGroup.addView(button, insertIndex, cloneLayoutParams(saveButton));
        return button;
    }

    /** Copies the save button's slot so the download icon matches its size and spacing. */
    private static ViewGroup.LayoutParams cloneLayoutParams(View saveButton) {
        ViewGroup.LayoutParams saveParams = saveButton.getLayoutParams();
        if (saveParams instanceof LinearLayout.LayoutParams) {
            return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) saveParams);
        }
        if (saveParams instanceof ViewGroup.MarginLayoutParams) {
            return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) saveParams);
        }
        if (saveParams != null) {
            return new ViewGroup.LayoutParams(saveParams.width, saveParams.height);
        }
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Resolves the icon and its tint against the row context. The global application context
     * cannot resolve activity scoped theme attributes such as `igds_color_primary_icon`.
     */
    private static void applyFeedDownloadIcon(ImageView button, Context context) {
        int drawableId = ResourceUtils.getIdentifier(context, ResourceType.DRAWABLE, UI.DRAWABLE_DOWNLOAD_ICON);
        if (drawableId == 0) return;
        button.setImageDrawable(context.getDrawable(drawableId));

        try {
            TypedValue typedValue = new TypedValue();
            int attrId = ResourceUtils.getAttrIdentifier("igds_color_primary_icon");
            if (attrId != 0
                    && context.getTheme().resolveAttribute(attrId, typedValue, true)
                    && typedValue.resourceId != 0) {
                button.setColorFilter(context.getColor(typedValue.resourceId));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * One-shot per message diagnostic. Some devices suppress app logcat output, so the same line
     * is appended to a cache file that can be pulled with root (piko-feed-download.log).
     */
    private static void logFeedDownloadButton(Context context, String message) {
        if (!feedDownloadButtonLogs.add(message)) return;
        Logger.printInfo(() -> "feed download button: " + message);
        try {
            // External cache first: on devices without root it can be pulled with
            // `adb shell cat /sdcard/Android/data/com.instagram.android/cache/piko-feed-download.log`.
            File dir = context.getExternalCacheDir();
            if (dir == null) dir = context.getCacheDir();
            File file = new File(dir, "piko-feed-download.log");
            if (file.length() > 64 * 1024) return;
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.append(new Date().toString()).append(' ').append(message).append('\n');
            }
        } catch (Exception ignored) {
        }
    }

    private static void enqueueDownload(Context context, DownloadRequest request) {
        if(!Utils.isNetworkConnected()){
            Utils.showToastShort(str("piko_no_internet"));
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        downloader.enqueue(request);
    }

    public static void externalDownloader(Object mediaObject, int currentMediaIndex){
        try {
            String packageName = Pref.externalDownloaderPackageName();
            packageName = packageName == null ? "" : packageName.trim();
            if(packageName.isEmpty()){
                PikoUtils.toast(str("piko_external_downloader_package_name_not_set"));
                return;
            }
            if(!PikoUtils.isAppInstalledAndEnabled(packageName)){
                PikoUtils.toast(str("piko_external_downloader_package_name_not_found"));
                return;
            }
            String link = Links.generatePostLink(mediaObject, currentMediaIndex);
            PikoUtils.shareTextToPackageName(link, packageName);
        } catch (Exception e){
            PikoUtils.logger(e);
            Logger.printException(() -> "Error at externalDownloader", e);
        }
    }
}
