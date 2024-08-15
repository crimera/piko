package android.support.constraint;

import android.content.Context;
import android.view.ViewGroup;

/**
 * "CompileOnly" class
 * because android.support.android.support.constraint.ConstraintLayout is deprecated
 * in favour of androidx.constraintlayout.widget.ConstraintLayout.
 *
 * This class will not be included and "replaced" by the real package's class.
 */
public class ConstraintLayout extends ViewGroup {
    public ConstraintLayout(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) { }
}
