/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;

public class DMActionBar {
    private static final String HIDE_ICON_NAME = "$avd_hide_password__0";
    private static final String SHOW_ICON_NAME = "$avd_show_password__0";

    // Drawable name for the "view deleted messages" action-bar button. setThemedIcon resolves
    // by getIdentifier and logs if absent, so a wrong name degrades gracefully (no crash).
    private static final String DELETED_MSGS_ICON = "basel_icons_svg_history";

    /**
     * The chat title (the other participant's username for a 1:1 chat) shown in the DM action bar,
     * read from the stable IG action-bar-title resource id. Returns null if not yet populated.
     */
    private static String extractChatTitle(ViewGroup viewGroup) {
        try {
            Context ctx = viewGroup.getContext();
            int id = ctx.getResources().getIdentifier(
                    "igds_action_bar_title", "id", ctx.getPackageName());
            if (id != 0) {
                View v = viewGroup.getRootView().findViewById(id);
                if (v instanceof android.widget.TextView) {
                    CharSequence cs = ((android.widget.TextView) v).getText();
                    if (cs != null && !cs.toString().trim().isEmpty()) return cs.toString().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            // Per-chat entry point for the "Save deleted messages" feature. Reliable because the
            // DM action bar is rebuilt every time a thread is opened (unlike the compose-bar
            // TextWatcher hook). Opens the deleted-messages screen scoped to the current thread.
            if (Pref.saveDeletedMessages()) {
                ImageView del = app.morphe.extension.instagram.constants.UI.addImageViewToViewGroup(
                    viewGroup, DELETED_MSGS_ICON,
                    // The open thread is tracked by SavedMessagesHook as its items arrive, so the
                    // screen scopes itself to the current chat. We only supply the title (read at
                    // click time, when the action-bar title is populated).
                    () -> {
                        String title = extractChatTitle(viewGroup);
                        if (title != null) {
                            app.morphe.extension.instagram.patches.dm.SavedMessagesHook
                                    .noteThreadTitle(title);
                        }
                        app.morphe.extension.instagram.patches.dm.SavedMessagesHook
                                .openDeletedMessages(viewGroup.getContext());
                    });
                if (del != null) {
                    del.setContentDescription(str("view_deleted_messages"));
                    int min = app.morphe.extension.shared.ui.Dim.dp48;
                    del.setMinimumWidth(min);
                    del.setMinimumHeight(min);
                }
                // Capture the title shortly after the bar is built (the title view populates
                // asynchronously) and bind it to the current thread, so notifications for unsends
                // in THIS chat show the sender's name even if the screen is never opened.
                viewGroup.postDelayed(() -> {
                    try {
                        String t = extractChatTitle(viewGroup);
                        if (t != null) {
                            app.morphe.extension.instagram.patches.dm.SavedMessagesHook
                                    .noteThreadTitle(t);
                        }
                    } catch (Throwable ignored) {}
                }, 700);
            }

            boolean togglePreference = Pref.enableGhostModeQuickToggle();
            boolean hasGhostSection = SettingsStatus.ghostSection();
            if(togglePreference && hasGhostSection){
                boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

                String iconStr = ghostModeToggle ? SHOW_ICON_NAME:HIDE_ICON_NAME;
                ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                            String iconStr = ghostModeToggle ? SHOW_ICON_NAME:HIDE_ICON_NAME;
                            Pref.setTurnOnAllGhostModes(ghostModeToggle);
                            UI.setThemedIcon(imageView,iconStr);

                            String toastStr = ghostModeToggle ? str("piko_ghost_modes_on") : str("piko_ghost_modes_default");
                            Utils.showToastShort(toastStr);
                        } catch (Exception ex) {
                            Logger.printException(() -> "ghost icon click failed: ", ex);
                        }
                    }
                });
            }

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}