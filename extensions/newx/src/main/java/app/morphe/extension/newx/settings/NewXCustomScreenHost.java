package app.morphe.extension.newx.settings;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.WeakHashMap;

import app.morphe.extension.newx.misc.UpdateFont;

/** Applies NewX settings theming to extension-owned custom screen views. */
public final class NewXCustomScreenHost implements ViewTreeObserver.OnGlobalLayoutListener {
    private final View root;
    private final ViewTreeObserver observer;
    private final WeakHashMap<TextView, Typeface> appliedTypefaces = new WeakHashMap<>();
    private boolean applying;
    private boolean closed;

    private NewXCustomScreenHost(View root) {
        this.root = root;
        if (root.getBackground() == null) {
            root.setBackgroundColor(NewXSettingsUi.backgroundColor(root.getContext()));
        }
        observer = root.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        applyTheme();
    }

    public static NewXCustomScreenHost attach(View root) {
        if (root == null) throw new IllegalArgumentException("Custom screen root is null");
        return new NewXCustomScreenHost(root);
    }

    @Override
    public void onGlobalLayout() {
        if (closed) return;
        applyTheme();
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(this);
        }
        appliedTypefaces.clear();
    }

    private void applyTheme() {
        if (applying || closed) return;
        applying = true;
        try {
            applyTheme(root);
        } finally {
            applying = false;
        }
    }

    private void applyTheme(View view) {
        if (view instanceof TextView textView) {
            applyTypeface(textView);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int index = 0; index < group.getChildCount(); index++) {
            applyTheme(group.getChildAt(index));
        }
    }

    private void applyTypeface(TextView textView) {
        Typeface current = textView.getTypeface();
        Typeface previous = appliedTypefaces.get(textView);
        if (previous != null && previous.equals(current)) return;

        Typeface themed = UpdateFont.customTypefaceOr(current);
        if (themed != current && !themed.equals(current)) {
            textView.setTypeface(themed);
        }
        appliedTypefaces.put(textView, textView.getTypeface());
    }
}
