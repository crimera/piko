package app.morphe.extension.newx.filteredreplies;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.newx.misc.UpdateFont;
import app.morphe.extension.newx.postfilter.VerifiedAccountWhitelistStore;
import app.morphe.extension.newx.ui.BottomSheetView;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.Utils;

/**
 * Bottom sheet dialog displaying replies hidden by the verified accounts check,
 * with an option to whitelist authors directly.
 */
public final class FilteredRepliesDialog {

    private FilteredRepliesDialog() {
    }

    public static void show(Activity activity, String postId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> show(activity, postId));
            return;
        }

        List<FilteredRepliesStore.FilteredReply> replies =
                FilteredRepliesStore.shared().getReplies(postId);

        if (replies.isEmpty()) {
            Utils.showToastShort("No filtered replies for this post");
            return;
        }

        BottomSheetView sheet = new BottomSheetView(activity);
        sheet.setTitle("Filtered replies");

        int count = replies.size();
        String subtitle = count == 1
                ? "1 reply hidden by verified accounts filter"
                : count + " replies hidden by verified accounts filter";
        sheet.setSubtitle(subtitle);

        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        Set<String> whitelist = VerifiedAccountWhitelistStore.shared().snapshot();
        Map<String, List<ButtonView>> authorButtons = new HashMap<>();

        for (int i = 0; i < count; i++) {
            FilteredRepliesStore.FilteredReply reply = replies.get(i);
            View row = createReplyRow(activity, reply, whitelist, authorButtons);
            listContainer.addView(row);

            if (i < count - 1) {
                View divider = createDivider(activity);
                listContainer.addView(divider);
            }
        }

        sheet.setScrollableBodyView(listContainer);
        sheet.show();
    }

    private static View createReplyRow(
            Context context,
            FilteredRepliesStore.FilteredReply reply,
            Set<String> initialWhitelist,
            Map<String, List<ButtonView>> authorButtons
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int padHoriz = Theme.dpToPx(context, 16f);
        int padVert = Theme.dpToPx(context, 12f);
        row.setPadding(padHoriz, padVert, padHoriz, padVert);

        // Ripple background
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Theme.rippleColor(context)),
                null,
                mask
        );
        row.setBackground(ripple);
        row.setClickable(true);
        row.setFocusable(true);

        // Long-click to copy text
        row.setOnLongClickListener(v -> {
            String text = reply.getPostText();
            if (!text.isEmpty()) {
                Utils.setClipboard(text);
                Utils.showToastShort("Copied reply text");
            }
            return true;
        });

        // Left column: author info + tweet text snippet
        LinearLayout leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        leftParams.setMarginEnd(Theme.dpToPx(context, 12f));
        leftColumn.setLayoutParams(leftParams);

        // Header row: @handle + verified badge chip
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView handleView = new TextView(context);
        handleView.setText("@" + reply.getAuthorScreenName());
        handleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        handleView.setTypeface(UpdateFont.customTypefaceOr(Typeface.DEFAULT_BOLD));
        handleView.setTextColor(Theme.primaryText(context));
        handleView.setSingleLine(true);
        handleView.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(handleView);

        String badgeType = reply.getAuthorVerifiedType();
        if (!badgeType.isEmpty()) {
            TextView badgeView = new TextView(context);
            badgeView.setText(badgeType);
            badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            badgeView.setTypeface(UpdateFont.customTypefaceOr(Typeface.DEFAULT));
            badgeView.setTextColor(Theme.secondaryText(context));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(Theme.dpToPx(context, 4f));
            badgeBg.setColor(Theme.surfaceVariant(context));
            badgeView.setBackground(badgeBg);

            int bPadH = Theme.dpToPx(context, 6f);
            int bPadV = Theme.dpToPx(context, 2f);
            badgeView.setPadding(bPadH, bPadV, bPadH, bPadV);

            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            badgeParams.setMarginStart(Theme.dpToPx(context, 8f));
            headerRow.addView(badgeView, badgeParams);
        }

        leftColumn.addView(headerRow);

        // Snippet text
        TextView textView = new TextView(context);
        String postText = reply.getPostText();
        if (postText.isEmpty()) {
            textView.setText("(No text content)");
            textView.setTypeface(UpdateFont.customTypefaceOr(Typeface.defaultFromStyle(Typeface.ITALIC)));
        } else {
            textView.setText(postText);
            textView.setTypeface(UpdateFont.customTypefaceOr(Typeface.DEFAULT));
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        textView.setTextColor(Theme.secondaryText(context));
        textView.setMaxLines(3);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(0, Theme.dpToPx(context, 4f), 0, 0);
        leftColumn.addView(textView);

        row.addView(leftColumn);

        // Right column: [+ Whitelist] button
        ButtonView button = new ButtonView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int btnPadH = Theme.dpToPx(context, 14f);
        button.setPadding(btnPadH, 0, btnPadH, 0);

        boolean isWhitelisted = VerifiedAccountWhitelistStore.matches(
                initialWhitelist,
                reply.getAuthorId(),
                reply.getAuthorScreenName()
        );

        if (isWhitelisted) {
            setWhitelistedState(button);
        } else {
            setAvailableState(button, reply.getAuthorScreenName(), authorButtons);
        }

        String key = reply.getAuthorScreenName().toLowerCase(Locale.ROOT);
        authorButtons.computeIfAbsent(key, k -> new ArrayList<>()).add(button);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Theme.dpToPx(context, 36f)
        );
        row.addView(button, btnParams);

        return row;
    }

    private static void setWhitelistedState(ButtonView button) {
        button.setText("Whitelisted ✓");
        button.setButtonStyle(ButtonView.ButtonStyle.TONAL);
        button.setEnabled(false);
        button.setOnClickListener(null);
    }

    private static void setAvailableState(
            ButtonView button,
            String screenName,
            Map<String, List<ButtonView>> authorButtons
    ) {
        button.setText("+ Whitelist");
        button.setButtonStyle(ButtonView.ButtonStyle.FILLED);
        button.setEnabled(true);
        button.setOnClickListener(v -> {
            try {
                VerifiedAccountWhitelistStore.shared().add(screenName);
                Utils.showToastShort("Added @" + screenName + " to whitelist");
            } catch (VerifiedAccountWhitelistStore.ValidationException e) {
                Utils.showToastShort("@" + screenName + " is already whitelisted");
            } catch (Exception e) {
                Utils.showToastShort("Failed to whitelist: " + e.getMessage());
            }
            updateAuthorButtons(authorButtons, screenName);
        });
    }

    private static void updateAuthorButtons(
            Map<String, List<ButtonView>> authorButtons,
            String screenName
    ) {
        String key = screenName.toLowerCase(Locale.ROOT);
        List<ButtonView> buttons = authorButtons.get(key);
        if (buttons != null) {
            for (ButtonView b : buttons) {
                setWhitelistedState(b);
            }
        }
    }

    private static View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.dividerColor(context));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Theme.dpToPx(context, 0.75f)
        ));
        return divider;
    }
}
