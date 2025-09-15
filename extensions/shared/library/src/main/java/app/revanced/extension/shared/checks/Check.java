package app.revanced.extension.shared.checks;

import static android.text.Html.FROM_HTML_MODE_COMPACT;
import static app.revanced.extension.shared.StringRef.str;
import static app.revanced.extension.shared.Utils.DialogFragmentOnStartAction;
import static app.revanced.extension.shared.Utils.dipToPixels;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.text.Html;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.Collection;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.BaseSettings;

abstract class Check {
    private static final int NUMBER_OF_TIMES_TO_IGNORE_WARNING_BEFORE_DISABLING = 2;

    private static final int SECONDS_BEFORE_SHOWING_IGNORE_BUTTON = 15;
    private static final int SECONDS_BEFORE_SHOWING_WEBSITE_BUTTON = 10;

    private static final Uri GOOD_SOURCE = Uri.parse("https://revanced.app");

    /**
     * @return If the check conclusively passed or failed. A null value indicates it neither passed nor failed.
     */
    @Nullable
    protected abstract Boolean check();

    protected abstract String failureReason();

    /**
     * Specifies a sorting order for displaying the checks that failed.
     * A lower value indicates to show first before other checks.
     */
    public abstract int uiSortingValue();

    /**
     * For debugging and development only.
     * Forces all checks to be performed and the check failed dialog to be shown.
     * Can be enabled by importing settings text with {@link BaseSettings#CHECK_ENVIRONMENT_WARNINGS_ISSUED}
     * set to -1.
     */
    static boolean debugAlwaysShowWarning() {
        final boolean alwaysShowWarning = BaseSettings.CHECK_ENVIRONMENT_WARNINGS_ISSUED.get() < 0;
        if (alwaysShowWarning) {
            Logger.printInfo(() -> "Debug forcing environment check warning to show");
        }

        return alwaysShowWarning;
    }

    static boolean shouldRun() {
        return BaseSettings.CHECK_ENVIRONMENT_WARNINGS_ISSUED.get()
                < NUMBER_OF_TIMES_TO_IGNORE_WARNING_BEFORE_DISABLING;
    }

    static void disableForever() {
        Logger.printInfo(() -> "Environment checks disabled forever");

        BaseSettings.CHECK_ENVIRONMENT_WARNINGS_ISSUED.save(Integer.MAX_VALUE);
    }

    @SuppressLint("NewApi")
    static void issueWarning(Activity activity, Collection<Check> failedChecks) {
        final var reasons = new StringBuilder();

        reasons.append("<ul>");
        for (var check : failedChecks) {
            // Add a non breaking space to fix bullet points spacing issue.
            reasons.append("<li>&nbsp;").append(check.failureReason());
        }
        reasons.append("</ul>");

        var message = Html.fromHtml(
                str("revanced_check_environment_failed_message", reasons.toString()),
                FROM_HTML_MODE_COMPACT
        );

        Utils.runOnMainThreadDelayed(() -> {
            // Create the custom dialog.
            Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                    activity,
                    str("revanced_check_environment_failed_title"), // Title.
                    message, // Message.
                    null,    // No EditText.
                    str("revanced_check_environment_dialog_open_official_source_button"), // OK button text.
                    () -> {
                        // Action for the OK (website) button.
                        final var intent = new Intent(Intent.ACTION_VIEW, GOOD_SOURCE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);

                        // Shutdown to prevent the user from navigating back to this app,
                        // which is no longer showing a warning dialog.
                        activity.finishAffinity();
                        System.exit(0);
                    },
                    null, // No cancel button.
                    str("revanced_check_environment_dialog_ignore_button"), // Neutral button text.
                    () -> {
                        // Neutral button action.
                        // Cleanup data if the user incorrectly imported a huge negative number.
                        final int current = Math.max(0, BaseSettings.CHECK_ENVIRONMENT_WARNINGS_ISSUED.get());
                        BaseSettings.CHECK_ENVIRONMENT_WARNINGS_ISSUED.save(current + 1);
                    },
                    true // Dismiss dialog when onNeutralClick.
            );

            // Get the dialog and main layout.
            Dialog dialog = dialogPair.first;
            LinearLayout mainLayout = dialogPair.second;

            // Add icon to the dialog.
            ImageView iconView = new ImageView(activity);
            iconView.setImageResource(Utils.getResourceIdentifier("revanced_ic_dialog_alert", "drawable"));
            iconView.setColorFilter(Utils.getAppForegroundColor(), PorterDuff.Mode.SRC_IN);
            iconView.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            iconParams.gravity = Gravity.CENTER;
            mainLayout.addView(iconView, 0); // Add icon at the top.

            dialog.setCancelable(false);

            // Show the dialog.
            Utils.showDialog(activity, dialog, false, new DialogFragmentOnStartAction() {
                boolean hasRun;
                @Override
                public void onStart(Dialog dialog) {
                    // Only run this once, otherwise if the user changes to a different app
                    // then changes back, this handler will run again and disable the buttons.
                    if (hasRun) {
                        return;
                    }
                    hasRun = true;

                    // Get the button container to access buttons.
                    LinearLayout buttonContainer = (LinearLayout) mainLayout.getChildAt(mainLayout.getChildCount() - 1);

                    Button openWebsiteButton;
                    Button ignoreButton;

                    // Check if buttons are in a single-row layout (buttonContainer has one child: rowContainer).
                    if (buttonContainer.getChildCount() == 1 && buttonContainer.getChildAt(0) instanceof LinearLayout) {
                        LinearLayout rowContainer = (LinearLayout) buttonContainer.getChildAt(0);
                        // Neutral button is the first child (index 0).
                        ignoreButton = (Button) rowContainer.getChildAt(0);
                        // OK button is the last child.
                        openWebsiteButton = (Button) rowContainer.getChildAt(rowContainer.getChildCount() - 1);
                    } else {
                        // Multi-row layout: buttons are in separate containers, ordered OK, Cancel, Neutral.
                        LinearLayout okContainer =
                                (LinearLayout) buttonContainer.getChildAt(0); // OK is first.
                        openWebsiteButton = (Button) okContainer.getChildAt(0);
                        LinearLayout neutralContainer =
                                (LinearLayout)buttonContainer.getChildAt(buttonContainer.getChildCount() - 1); // Neutral is last.
                        ignoreButton = (Button) neutralContainer.getChildAt(0);
                    }

                    // Initially set buttons to INVISIBLE and disabled.
                    openWebsiteButton.setVisibility(View.INVISIBLE);
                    openWebsiteButton.setEnabled(false);
                    ignoreButton.setVisibility(View.INVISIBLE);
                    ignoreButton.setEnabled(false);

                    // Start the countdown for showing and enabling buttons.
                    getCountdownRunnable(ignoreButton, openWebsiteButton).run();
                }
            });
        }, 1000); // Use a delay, so this dialog is shown on top of any other startup dialogs.
    }

    private static Runnable getCountdownRunnable(Button ignoreButton, Button openWebsiteButton) {
        return new Runnable() {
            private int secondsRemaining = SECONDS_BEFORE_SHOWING_IGNORE_BUTTON;

            @Override
            public void run() {
                Utils.verifyOnMainThread();

                if (secondsRemaining > 0) {
                    if (secondsRemaining - SECONDS_BEFORE_SHOWING_WEBSITE_BUTTON <= 0) {
                        openWebsiteButton.setVisibility(View.VISIBLE);
                        openWebsiteButton.setEnabled(true);
                    }
                    secondsRemaining--;
                    Utils.runOnMainThreadDelayed(this, 1000);
                } else {
                    ignoreButton.setVisibility(View.VISIBLE);
                    ignoreButton.setEnabled(true);
                }
            }
        };
    }
}
