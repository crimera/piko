/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** Chat categories ("folders") for the DM inbox: storage, long-press menu row and dialogs. */
@SuppressWarnings("unused")
public final class DirectOrganizer {
    private static final String KEY_CATEGORIES = "piko_chat_categories";
    private static final String KEY_ASSIGNMENTS = "piko_chat_category_assignments";
    private static final String KEY_ACTIVE_FOLDER = "piko_chat_active_folder";
    private static final String KEY_UNREAD = "piko_chat_unread_keys";
    private static final String KEY_ROW_OFFSET = "piko_chat_row_offset";
    private static final char SEPARATOR = '\u0001';

    private DirectOrganizer() {
    }

    private static List<String> readLines(String key) {
        List<String> lines = new ArrayList<>();
        String raw = DirectOrganizerPref.getStringPref(key);
        if (raw.isEmpty()) return lines;
        for (String line : raw.split("\n")) {
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static void writeLines(String key, Set<String> lines) {
        DirectOrganizerPref.setStringPref(key, String.join("\n", lines));
    }

    public static Set<String> getCategories() {
        return new LinkedHashSet<>(readLines(KEY_CATEGORIES));
    }

    public static void createCategory(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return;
        Set<String> categories = getCategories();
        if (categories.add(trimmed)) writeLines(KEY_CATEGORIES, categories);
    }

    public static boolean renameCategory(String oldName, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        Set<String> categories = getCategories();
        if (trimmed.isEmpty() || trimmed.equals(oldName) || categories.contains(trimmed)) {
            return false;
        }

        Set<String> renamed = new LinkedHashSet<>();
        for (String category : categories) renamed.add(category.equals(oldName) ? trimmed : category);
        writeLines(KEY_CATEGORIES, renamed);

        Set<String> rows = new LinkedHashSet<>();
        for (String row : readLines(KEY_ASSIGNMENTS)) {
            int split = row.indexOf(SEPARATOR);
            rows.add(split > 0 && row.substring(0, split).equals(oldName) ? trimmed + row.substring(split) : row);
        }
        writeLines(KEY_ASSIGNMENTS, rows);
        if (activeFolder().equals(oldName)) setActiveFolder(trimmed);
        InboxFolderBar.onFoldersChanged();
        return true;
    }

    /** Deletes a folder; its chats go back to the main list. */
    public static void deleteCategory(String name) {
        Set<String> categories = getCategories();
        if (categories.remove(name)) writeLines(KEY_CATEGORIES, categories);

        Set<String> keys = threadsOf(name);
        Set<String> rows = new LinkedHashSet<>();
        for (String row : readLines(KEY_ASSIGNMENTS)) {
            int split = row.indexOf(SEPARATOR);
            if (split <= 0 || !row.substring(0, split).equals(name)) rows.add(row);
        }
        writeLines(KEY_ASSIGNMENTS, rows);

        for (String key : keys) InboxFolderBar.forgetRow(key);
        Set<String> unread = getUnread();
        if (unread.removeAll(keys)) writeLines(KEY_UNREAD, unread);
        if (activeFolder().equals(name)) setActiveFolder("");
        InboxFolderBar.onFoldersChanged();
    }

    public static void assignThread(String threadKey, String category) {
        Set<String> rows = new LinkedHashSet<>(readLines(KEY_ASSIGNMENTS));
        rows.removeIf(row -> row.endsWith(SEPARATOR + threadKey));
        rows.add(category + SEPARATOR + threadKey);
        writeLines(KEY_ASSIGNMENTS, rows);
        InboxFolderBar.onFoldersChanged();
    }

    public static void unassignThread(String threadKey) {
        InboxFolderBar.forgetRow(threadKey);
        Set<String> rows = new LinkedHashSet<>(readLines(KEY_ASSIGNMENTS));
        if (rows.removeIf(row -> row.endsWith(SEPARATOR + threadKey))) {
            writeLines(KEY_ASSIGNMENTS, rows);
            InboxFolderBar.onFoldersChanged();
        }
    }

    /** Thread key to folder name, for every categorized chat. */
    public static Map<String, String> getAssignments() {
        Map<String, String> map = new HashMap<>();
        for (String row : readLines(KEY_ASSIGNMENTS)) {
            int split = row.indexOf(SEPARATOR);
            if (split > 0) map.put(row.substring(split + 1), row.substring(0, split));
        }
        return map;
    }

    public static String categoryOf(String threadKey) {
        return getAssignments().get(threadKey);
    }

    public static Set<String> threadsOf(String category) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : getAssignments().entrySet()) {
            if (entry.getValue().equals(category)) keys.add(entry.getKey());
        }
        return keys;
    }

    /** The selected folder, or an empty string for the main list. */
    public static String activeFolder() {
        return DirectOrganizerPref.getStringPref(KEY_ACTIVE_FOLDER);
    }

    public static void setActiveFolder(String category) {
        DirectOrganizerPref.setStringPref(KEY_ACTIVE_FOLDER, category == null ? "" : category);
    }

    /** Thread keys of categorized chats last seen with unread messages. */
    public static Set<String> getUnread() {
        return new LinkedHashSet<>(readLines(KEY_UNREAD));
    }

    public static void setUnread(Set<String> keys) {
        writeLines(KEY_UNREAD, keys);
    }

    /** Difference between a row's adapter position and its index in the inbox item list. */
    public static int rowOffset() {
        return DirectOrganizerPref.getIntPref(KEY_ROW_OFFSET, 0);
    }

    public static void setRowOffset(int offset) {
        DirectOrganizerPref.setIntPref(KEY_ROW_OFFSET, offset);
    }

    private static final class CategorizeClickListener implements View.OnClickListener {
        private final Context context;
        private final String threadKey;

        CategorizeClickListener(Context context, String threadKey) {
            this.context = context;
            this.threadKey = threadKey;
        }

        @Override
        public void onClick(View v) {
            if (categoryOf(threadKey) != null) {
                unassignThread(threadKey);
                PikoUtils.toast(str("piko_dm_chat_uncategorized"));
            } else if (context instanceof Activity) {
                showCategorizeDialog((Activity) context, threadKey);
            } else {
            }
        }
    }

    /** Called from the long-press menu builder once the native rows are added. */
    public static void onThreadMenuBuilt(Object sheetConfig, Object threadKeyObj, View anchorView) {
        try {
            String threadKey = String.valueOf(threadKeyObj);
            InboxFolderBar.calibrate(anchorView, threadKey);
            String label = str(categoryOf(threadKey) != null ? "piko_dm_uncategorize" : "piko_dm_categorize");

            // The config has two (String, OnClickListener) methods; A0A adds the normal (white) row.
            Method rowMethod = null;
            for (Method method : sheetConfig.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getReturnType() == void.class && params.length == 2
                        && params[0] == String.class && params[1] == View.OnClickListener.class
                        && (rowMethod == null || method.getName().equals("A0A"))) {
                    rowMethod = method;
                }
            }
            if (rowMethod == null) {
                Logger.printException(() -> "DirectOrganizer: no row method found on the menu config");
                return;
            }
            rowMethod.invoke(sheetConfig, label, new CategorizeClickListener(anchorView.getContext(), threadKey));
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer menu hook failed", e);
        }
    }

    public static void showCategorizeDialog(Activity activity, String threadKey) {
        showNameDialog(activity, str("piko_dm_categorize_title"), "", str("piko_dm_create"), name -> {
            createCategory(name);
            assignChat(threadKey, name);
        }, name -> assignChat(threadKey, name));
    }

    private static void assignChat(String threadKey, String name) {
        assignThread(threadKey, name);
        PikoUtils.toast(str("piko_dm_chat_categorized", name));
    }

    public static void showRenameDialog(Activity activity, String oldName) {
        showNameDialog(activity, str("piko_dm_rename_title"), oldName, str("piko_dm_save"), name -> {
            if (!renameCategory(oldName, name)) PikoUtils.toast(str("piko_dm_invalid_name"));
        }, null);
    }

    private static GradientDrawable translucentBackground(float density) {
        int surface = UI.getThemedColour("igds_color_elevated_background");
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(0x99, Color.red(surface), Color.green(surface), Color.blue(surface)));
        background.setCornerRadius(14 * density);
        return background;
    }

    private static TextView button(Context context, String text, boolean bold, int pad) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(UI.getThemedColour("igds_color_link"));
        view.setTextSize(17);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, pad, 0, pad);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static View line(Context context, int color, int width, int height) {
        View line = new View(context);
        line.setBackgroundColor(color);
        line.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return line;
    }

    private static void showNameDialog(Activity activity, String titleText, String initial,
                                       String confirmText, Consumer<String> onConfirm,
                                       Consumer<String> onPickExisting) {
        try {
            float density = activity.getResources().getDisplayMetrics().density;
            int textColor = UI.getThemedColour("igds_color_primary_text");
            int separator = UI.getThemedColour("igds_color_separator");
            int pad = (int) (density * 16);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(translucentBackground(density));

            TextView title = new TextView(activity);
            title.setText(titleText);
            title.setTextColor(textColor);
            title.setTextSize(17);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(pad, pad, pad, pad / 2);
            card.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
            input.setTextColor(textColor);
            input.setHintTextColor(UI.getThemedColour("igds_color_secondary_text"));
            input.setHint(str("piko_dm_category_hint"));
            input.setText(initial);
            input.setSelection(initial.length());
            input.setBackground(null);
            input.setGravity(Gravity.CENTER);
            input.setTextSize(16);
            input.setPadding(pad, (int) (density * 14), pad, (int) (density * 22));
            card.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            card.addView(line(activity, separator, LayoutParams.MATCH_PARENT, (int) density));
            TextView cancel = button(activity, str("piko_cancel"), false, pad);
            TextView confirm = button(activity, confirmText, true, pad);
            LinearLayout buttons = new LinearLayout(activity);
            buttons.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            buttons.addView(line(activity, separator, (int) density, LayoutParams.MATCH_PARENT));
            buttons.addView(confirm, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            card.addView(buttons, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            AlertDialog[] dialogRef = new AlertDialog[1];
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(card, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            Set<String> existing = getCategories();
            if (onPickExisting != null && !existing.isEmpty()) {
                root.addView(buildCategoryList(activity, existing, density, name -> {
                    dialogRef[0].dismiss();
                    onPickExisting.accept(name);
                }));
            }

            AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
            dialogRef[0] = dialog;
            cancel.setOnClickListener(v -> {
                dialog.dismiss();
            });
            confirm.setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) onConfirm.accept(name);
                dialog.dismiss();
            });

            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.show();
            window.setLayout((int) (270 * density), LayoutParams.WRAP_CONTENT);
            input.requestFocus();
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        } catch (Exception e) {
            Logger.printException(() -> "DirectOrganizer dialog failed", e);
        }
    }

    private static View buildCategoryList(Activity activity, Set<String> names, float density,
                                          Consumer<String> onPick) {
        int rowHeight = (int) (density * 46);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        boolean first = true;
        for (String name : names) {
            if (!first) list.addView(line(activity, UI.getThemedColour("igds_color_separator"), LayoutParams.MATCH_PARENT, Math.max(1, (int) (density * 0.5f))));
            first = false;
            TextView item = new TextView(activity);
            item.setText(name);
            item.setTextColor(UI.getThemedColour("igds_color_primary_text"));
            item.setTextSize(16);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding((int) (density * 18), 0, (int) (density * 18), 0);
            item.setOnClickListener(v -> onPick.accept(name));
            list.addView(item, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        scroll.setBackground(translucentBackground(density));
        scroll.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, names.size() > 5 ? rowHeight * 5 : LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (density * 10);
        scroll.setLayoutParams(params);
        return scroll;
    }
}
