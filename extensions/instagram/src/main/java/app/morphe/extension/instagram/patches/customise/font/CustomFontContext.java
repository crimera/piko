/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.font;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * A themed context whose inflater applies the custom font, for the platform dialogs piko shows:
 * their text views are inflated by the framework rather than created in piko's code.
 */
public final class CustomFontContext extends ContextThemeWrapper {
    private LayoutInflater inflater;

    public CustomFontContext(Context base, int themeResId) {
        super(base, themeResId);
    }

    @Override
    public Object getSystemService(String name) {
        if (!LAYOUT_INFLATER_SERVICE.equals(name)) {
            return super.getSystemService(name);
        }
        if (inflater == null) {
            // A clone owned by this context, so setting a factory on it affects nothing else.
            inflater = (LayoutInflater) super.getSystemService(name);
            if (CustomFont.isActive()) {
                inflater.setFactory2(new FontFactory(inflater));
            }
        }
        return inflater;
    }

    private static final class FontFactory implements LayoutInflater.Factory2 {
        private static final String[] PREFIXES = {
                "android.widget.", "android.webkit.", "android.app.", "android.view."
        };

        private final LayoutInflater.Factory original;

        FontFactory(LayoutInflater inflater) {
            this.original = inflater.getFactory();
        }

        @Override
        public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
            View view = null;
            if (original instanceof LayoutInflater.Factory2) {
                view = ((LayoutInflater.Factory2) original).onCreateView(parent, name, context, attrs);
            } else if (original != null) {
                view = original.onCreateView(name, context, attrs);
            }
            if (view == null) {
                view = create(name, context, attrs);
            }
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                Typeface current = textView.getTypeface();
                Typeface replacement = CustomFont.forPikoView(current);
                if (replacement != current) {
                    textView.setTypeface(replacement);
                }
            }
            return view;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            return onCreateView(null, name, context, attrs);
        }

        /** Creates the view the way the platform inflater would, or leaves it to the platform. */
        private View create(String name, Context context, AttributeSet attrs) {
            if (name.indexOf('.') != -1) {
                return createView(name, null, context, attrs);
            }
            for (String prefix : PREFIXES) {
                View view = createView(name, prefix, context, attrs);
                if (view != null) {
                    return view;
                }
            }
            return null;
        }

        private View createView(String name, String prefix, Context context, AttributeSet attrs) {
            // The inflater doing the inflation: before API 29 it builds the view in the context it
            // is inflating in, rather than in one that is passed.
            LayoutInflater inflater = LayoutInflater.from(context);
            try {
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? inflater.createView(context, name, prefix, attrs)
                        : inflater.createView(name, prefix, attrs);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
