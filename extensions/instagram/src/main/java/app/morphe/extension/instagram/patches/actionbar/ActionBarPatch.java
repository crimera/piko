/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;

import com.instagram.common.session.UserSession;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

public class ActionBarPatch {

    private static final Set<ImageView> GHOST_MODE_ICONS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> HOOKED_VIEWS = Collections.newSetFromMap(new WeakHashMap<>());
    private static int directTabResId = 0;

    private static void updateGhostModeIcons(boolean enabled) {
        String icon = enabled ? UI.DRAWABLE_EYE_STROKE_ICON : UI.DRAWABLE_EYE_ICON;
        for (ImageView imageView : GHOST_MODE_ICONS) {
            if (imageView != null) {
                UI.setThemedIcon(imageView, icon);
            }
        }
    }

    public static void toggleGhostMode() {
        try {
            boolean isGhostMode = Pref.getTurnOnAllGhostModes();
            boolean ghostModeToggle = !isGhostMode;
            Pref.setTurnOnAllGhostModes(ghostModeToggle);

            updateGhostModeIcons(ghostModeToggle);

            String toastStr = ghostModeToggle ? str("piko_ghost_modes_on") : str("piko_ghost_modes_default");
            Utils.showToastShort(toastStr);
        } catch (Exception ex) {
            Logger.printException(() -> "toggleGhostMode failed: ", ex);
        }
    }

    private static final View.OnLongClickListener GHOST_MODE_LONG_CLICK = v -> {
        if (!Pref.longPressInboxToggleGhostMode()) {
            return false;
        }
        if (v != null) {
            try {
                v.setHapticFeedbackEnabled(true);
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Throwable ignored) {}
        }
        toggleGhostMode();
        return true;
    };

    public static void hookInboxButton(View view) {
        if (view == null) return;
        synchronized (HOOKED_VIEWS) {
            if (!HOOKED_VIEWS.add(view)) {
                return;
            }
        }
        view.setOnLongClickListener(GHOST_MODE_LONG_CLICK);
    }

    public static void hookTabButton(View view) {
        if (view == null) return;
        if (view.isAttachedToWindow()) {
            view.post(() -> identifyAndHookDirectTab(view));
            return;
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                v.post(() -> identifyAndHookDirectTab(v));
            }

            @Override
            public void onViewDetachedFromWindow(View v) {}
        });
    }

    private static void identifyAndHookDirectTab(View view) {
        if (view == null) return;
        synchronized (HOOKED_VIEWS) {
            if (HOOKED_VIEWS.contains(view)) {
                return;
            }
        }
        try {
            if (directTabResId == 0) {
                try {
                    directTabResId = ResourceUtils.getIdentifier(ResourceType.ID, "direct_tab");
                } catch (Throwable ignored) {}
            }

            int id = view.getId();
            String entryName = null;
            if (id != View.NO_ID) {
                try {
                    entryName = view.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {}
            }
            Object tag = view.getTag();
            CharSequence desc = view.getContentDescription();
            if (directTabResId != 0 && id == directTabResId) {
                hookInboxButton(view);
                return;
            }

            if (id != View.NO_ID) {
                try {
                    if ("direct_tab".equals(entryName)) {
                        hookInboxButton(view);
                        return;
                    }
                } catch (Throwable ignored) {}
            }

            if (tag != null) {
                String tagStr = tag.toString().toLowerCase();
                if (tagStr.contains("direct") || tagStr.contains("inbox")) {
                    hookInboxButton(view);
                    return;
                }
            }

            if (desc != null) {
                String descLower = desc.toString().toLowerCase();
                if (descLower.contains("direct") || descLower.contains("message") || descLower.contains("inbox")) {
                    hookInboxButton(view);
                    return;
                }
            }

            if (directTabResId != 0 && view instanceof ViewGroup) {
                View directTabChild = view.findViewById(directTabResId);
                if (directTabChild != null) {
                    hookInboxButton(directTabChild);
                    return;
                }
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "identifyAndHookDirectTab failed", ex);
        }
    }

    private static void ghostModeToggle(ViewGroup viewGroup) throws Exception {
        if(SettingsStatus.ghostSection()){
            boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

            String iconStr = ghostModeToggle ? UI.DRAWABLE_EYE_STROKE_ICON:UI.DRAWABLE_EYE_ICON;
            ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
            GHOST_MODE_ICONS.add(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleGhostMode();
                }
            });
        }
    }

    public static void mainFeedActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.mainFeedActionBarButtons();

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            hookDirectTabFromRoot(viewGroup);

        } catch (Exception e) {
            Logger.printException(() -> "mainFeedActionBarButton failure", e);
            PikoUtils.logger(e);
        }
    }

    public static void hookDirectTabFromRoot(View viewGroup) {
        try {
            if (viewGroup == null) return;
            View root = viewGroup.getRootView();
            if (root == null) root = viewGroup;
            final View searchRoot = root;
            if (!tryHookDirectTab(searchRoot)) {
                searchRoot.postDelayed(() -> {
                    try {
                        if (!tryHookDirectTab(searchRoot)) {
                            searchRoot.postDelayed(() -> tryHookDirectTab(searchRoot), 1000);
                        }
                    } catch (Throwable ignored) {}
                }, 500);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean tryHookDirectTab(View searchRoot) {
        try {
            if (directTabResId == 0) {
                try {
                    directTabResId = ResourceUtils.getIdentifier(ResourceType.ID, "direct_tab");
                } catch (Throwable ignored) {
                    return false;
                }
            }
            if (directTabResId == 0) {
                return false;
            }
            View directTab = searchRoot.findViewById(directTabResId);
            if (directTab == null) {
                return false;
            }
            hookInboxButton(directTab);
            directTab.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                try {
                    hookInboxButtonLenient(v);
                } catch (Throwable ignored) {}
            });
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hookInboxButtonLenient(View view) {
        if (view == null) return;
        try {
            view.setOnLongClickListener(GHOST_MODE_LONG_CLICK);
        } catch (Throwable ignored) {}
    }

    public static void userProfileActionBarButton(Activity activity, ViewGroup viewGroup, UserSession userSession, Object userObject){
        try {
            if (activity == null || viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.userProfileActionBarButtons();

            UserData userData = new UserData(userObject);
            Boolean isSelfProfile = userData.getUserId().equals(userSession.getUserId());

            if(pref.contains(Constants.AB_SETTINGS_ICON) && isSelfProfile) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON) && isSelfProfile) {
                ghostModeToggle(viewGroup);
            }

            if(pref.contains(Constants.AB_PROFILE_INFO_ICON)) {
                UI.addImageViewToViewGroup(viewGroup, UI.DRAWABLE_INFO_ICON, () -> ProfileMoreOption.moreOptionsDailogueBox(activity, userData));
            }


        } catch (Exception e) {
            Logger.printException(() -> "userProfileActionBarButton: ", e);
            PikoUtils.logger(e);
        }
    }

    public static void chatActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.chatActionBarButtons();

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "chatActionBarButton:", e);
        }
    }

    public static void inboxActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.inboxActionBarButtons();

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "inboxActionBarButton:", e);
        }
    }

}
