package app.morphe.extension.shared.settings.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Empty preference category with no title, used to organize and group related preferences together.
 */
@SuppressWarnings({"unused", "deprecation"})
public class NoTitlePreferenceCategory extends PreferenceCategory {

    public NoTitlePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoTitlePreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NoTitlePreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NoTitlePreferenceCategory(Context context) {
        super(context);
    }

    @Override
    @SuppressLint("MissingSuperCall")
    protected View onCreateView(ViewGroup parent) {
        // Return an zero-height view to eliminate empty title space.
        return new View(getContext());
    }

    @Override
    public CharSequence getTitle() {
        // Title can be used for sorting. Return the first sub preference title.
        if (getPreferenceCount() > 0) {
            return getPreference(0).getTitle();
        }

        return super.getTitle();
    }

    @Override
    public int getTitleRes() {
        if (getPreferenceCount() > 0) {
            return getPreference(0).getTitleRes();
        }

        return super.getTitleRes();
    }
}

