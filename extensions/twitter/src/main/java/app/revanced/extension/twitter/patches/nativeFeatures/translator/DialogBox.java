package app.revanced.extension.twitter.patches.nativeFeatures.translator;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.revanced.extension.shared.Utils;

public class DialogBox extends Dialog {
    private final String header;
    private final String mainText;
    private final Context context;

    public DialogBox(Context context, String header,String mainText) {
        super(context);
        this.context = context;
        this.header = header;
        this.mainText = mainText;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Main container
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 30, 50, 30);

        // Header TextView
        TextView headerView = new TextView(context);
        headerView.setText(header);
        headerView.setTextSize(20);
        headerView.setTextColor(Color.GRAY);
        headerView.setGravity(Gravity.CENTER);
        headerView.setTextIsSelectable(false);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 0, 0, 20);
        headerView.setLayoutParams(headerParams);

        // Main Text (Selectable and Scrollable)
        TextView mainTextView = new TextView(context);
        mainTextView.setText(mainText);
        mainTextView.setTextSize(16);
        mainTextView.setTextIsSelectable(true);
        mainTextView.setMovementMethod(new ScrollingMovementMethod());
        mainTextView.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams mainTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                700  // Fixed height for scrollable area
        );
        mainTextParams.setMargins(0, 0, 0, 10);
        mainTextView.setLayoutParams(mainTextParams);

        // Button Container
        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setWeightSum(2);

        // Copy Button
        Button copyButton = new Button(context);
        copyButton.setText("Copy");
        LinearLayout.LayoutParams copyButtonParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        copyButtonParams.setMargins(0, 0, 10, 0);
        copyButton.setLayoutParams(copyButtonParams);

        // Close Button
        Button closeButton = new Button(context);
        closeButton.setText("Close");
        LinearLayout.LayoutParams closeButtonParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        closeButtonParams.setMargins(10, 0, 0, 0);
        closeButton.setLayoutParams(closeButtonParams);

        // Button Click Listeners
        copyButton.setOnClickListener(v -> {
            Utils.setClipboard(mainText);
            Utils.showToastLong("Text copied to clipboard");
        });

        closeButton.setOnClickListener(v -> dismiss());

        // Add all views to their containers
        buttonLayout.addView(copyButton);
        buttonLayout.addView(closeButton);

        mainLayout.addView(headerView);
        mainLayout.addView(mainTextView);
        mainLayout.addView(buttonLayout);

        setContentView(mainLayout);
    }
}