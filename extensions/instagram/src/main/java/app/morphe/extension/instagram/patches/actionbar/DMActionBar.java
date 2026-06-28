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
    private static final String HIDE_ICON_NAME = "design_ic_visibility_off";
    private static final String SHOW_ICON_NAME = "design_ic_visibility";

    // Drawable name for the "view deleted messages" action-bar button. setThemedIcon resolves
    // by getIdentifier and logs if absent, so a wrong name degrades gracefully (no crash).
    private static final String DELETED_MSGS_ICON = "basel_icons_svg_history";

    /**
     * Find the chat title shown in the DM action bar (the other participant's username for a 1:1
     * chat). Walks up to the action-bar root and returns the first plausible TextView text:
     * non-empty, not a pure timestamp/"Active" status, preferring the largest text size (title).
     */
    private static String extractChatTitle(ViewGroup viewGroup) {
        try {
            android.view.View root = viewGroup.getRootView();
            Context ctx = viewGroup.getContext();
            // The DM action bar title lives in a TextView with this stable IG resource-id.
            int id = ctx.getResources().getIdentifier(
                    "igds_action_bar_title", "id", ctx.getPackageName());
            if (id != 0) {
                android.view.View v = root.findViewById(id);
                if (v instanceof android.widget.TextView) {
                    CharSequence cs = ((android.widget.TextView) v).getText();
                    if (cs != null && !cs.toString().trim().isEmpty()) return cs.toString().trim();
                }
            }
            // Fallback: smallest plausible name-like TextView near the top (avoids button labels).
            java.util.List<android.widget.TextView> tvs = new java.util.ArrayList<>();
            collectTextViews(root, tvs);
            for (android.widget.TextView tv : tvs) {
                int[] loc = new int[2];
                tv.getLocationOnScreen(loc);
                if (loc[1] > 320) continue; // title sits in the top bar
                CharSequence cs = tv.getText();
                if (cs == null) continue;
                String s = cs.toString().trim();
                if (s.isEmpty() || s.length() > 40) continue;
                String low = s.toLowerCase();
                if (low.equals("send") || low.equals("active") || low.contains("ago")
                        || low.contains("typing") || s.matches("\\d{1,2}:\\d{2}.*")) continue;
                return s;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void collectTextViews(android.view.View v, java.util.List<android.widget.TextView> out) {
        if (v instanceof android.widget.TextView) out.add((android.widget.TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectTextViews(g.getChildAt(i), out);
        }
    }

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            // Per-chat entry point for the "Save deleted messages" feature. Reliable because the
            // DM action bar is rebuilt every time a thread is opened (unlike the compose-bar
            // TextWatcher hook). Opens the deleted-messages screen scoped to the current thread.
            if (Pref.saveDeletedMessages()) {
                ImageView del = app.morphe.extension.instagram.constants.UI.addImageViewToViewGroup(
                    viewGroup, DELETED_MSGS_ICON,
                    // The open thread id is recorded at patch time from the action-bar builder
                    // (noteOpenThreadId), so the screen scopes itself. We only supply the title,
                    // read at click time when the action-bar title TextView is populated.
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
                // Bind the chat title to the current thread shortly after the bar is built (the
                // title view populates asynchronously) so unsend notifications in THIS chat show
                // the name even if the deleted-messages screen is never opened.
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

                String iconStr = ghostModeToggle ? HIDE_ICON_NAME:SHOW_ICON_NAME;
                ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                            String iconStr = ghostModeToggle ? HIDE_ICON_NAME:SHOW_ICON_NAME;
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