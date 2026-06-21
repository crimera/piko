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

    /**
     * Resolve the open chat's thread_id at click time by reflecting the host Activity for IG's
     * DirectThreadKey. A0P-based tracking is unreliable (it fires for feed clips, not the open
     * chat), so we walk the live Activity object graph instead. Bounded BFS over IG/obfuscated
     * objects only; prefers a DirectThreadKey's numeric id, falls back to a long numeric String.
     */
    private static String resolveThreadId(android.view.View anchor) {
        try {
            android.content.Context c = anchor.getContext();
            android.app.Activity act = null;
            while (c instanceof android.content.ContextWrapper) {
                if (c instanceof android.app.Activity) { act = (android.app.Activity) c; break; }
                c = ((android.content.ContextWrapper) c).getBaseContext();
            }
            if (act == null) return null;

            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            q.add(act); seen.put(act, Boolean.TRUE);
            String numericFallback = null;
            int budget = 20000;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                Class<?> k = o.getClass();
                // Object arrays (e.g. ArrayList/HashMap internal storage) — enqueue elements so
                // the walk can reach IG fragments held inside collections.
                if (k.isArray() && !k.getComponentType().isPrimitive()) {
                    for (Object el : (Object[]) o) {
                        if (el != null && seen.put(el, Boolean.TRUE) == null) q.add(el);
                    }
                    continue;
                }
                String kn = k.getName();
                if (k.getSimpleName().contains("ThreadKey") || kn.contains("ThreadKey")) {
                    String id = firstDigitField(o);
                    if (id != null && id.matches("\\d{30,}")) return id; // best signal
                }
                boolean descend = kn.startsWith("com.instagram") || kn.startsWith("X.")
                        || kn.startsWith("androidx.fragment") || kn.startsWith("androidx.lifecycle")
                        || kn.startsWith("java.util");
                if (!descend) continue;
                for (Class<?> cc = k; cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                    for (java.lang.reflect.Field f : cc.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isPrimitive()) continue;
                        Object v;
                        try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                        if (v == null || seen.put(v, Boolean.TRUE) != null) continue;
                        if (v instanceof String) {
                            String s = (String) v;
                            // thread_id is the LONGEST numeric id (~39 digits) — outranks msg/user ids.
                            if (s.matches("\\d{30,}") && (numericFallback == null || s.length() > numericFallback.length())) {
                                numericFallback = s;
                            }
                            continue;
                        }
                        if (v instanceof android.os.Bundle) {
                            android.os.Bundle b = (android.os.Bundle) v;
                            for (String key : b.keySet()) {
                                Object bv;
                                try { bv = b.get(key); } catch (Throwable t) { continue; }
                                if (bv == null) continue;
                                if (bv instanceof String && ((String) bv).matches("\\d{30,}")
                                        && (numericFallback == null || ((String) bv).length() > numericFallback.length())) {
                                    numericFallback = (String) bv;
                                } else if (!(bv instanceof CharSequence) && !(bv instanceof Number)
                                        && !(bv instanceof Boolean) && seen.put(bv, Boolean.TRUE) == null) {
                                    q.add(bv); // Parcelable thread key etc.
                                }
                            }
                            continue;
                        }
                        if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                        q.add(v);
                    }
                }
            }
            return numericFallback;
        } catch (Throwable t) {
            return null;
        }
    }

    /** First all-digit String field (>=10 digits) on obj — the thread_id on a DirectThreadKey. */
    private static String firstDigitField(Object obj) {
        for (Class<?> cc = obj.getClass(); cc != null && cc != Object.class; cc = cc.getSuperclass()) {
            for (java.lang.reflect.Field f : cc.getDeclaredFields()) {
                if (f.getType() != String.class) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof String && ((String) v).matches("\\d{10,}")) return (String) v;
                } catch (Throwable ignored) {}
            }
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
                    // Resolve title + thread_id at CLICK time — when the thread is fully open and
                    // its action-bar title is populated (both are null/empty at build time).
                    () -> {
                        String title = extractChatTitle(viewGroup);
                        String threadId = resolveThreadId(viewGroup);
                        app.morphe.extension.instagram.patches.dm.SavedMessagesHook
                                .openDeletedMessagesFor(viewGroup.getContext(), threadId, title);
                    });
                if (del != null) {
                    del.setContentDescription("View deleted messages");
                    int min = app.morphe.extension.shared.ui.Dim.dp48;
                    del.setMinimumWidth(min);
                    del.setMinimumHeight(min);
                }
                // Capture title + thread_id shortly after the bar is built (the title view
                // populates asynchronously) so that notifications for unsends in THIS chat show
                // the sender's name even when the user never opens the deleted-messages screen.
                viewGroup.postDelayed(() -> {
                    try {
                        String t = extractChatTitle(viewGroup);
                        String tid = resolveThreadId(viewGroup);
                        app.morphe.extension.instagram.patches.dm.SavedMessagesHook
                                .noteThreadOpen(tid, t);
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