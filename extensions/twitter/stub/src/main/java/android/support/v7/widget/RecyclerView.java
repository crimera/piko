package android.support.v7.widget;

import android.content.Context;
import android.view.View;

public class RecyclerView extends View {

    public RecyclerView(Context context) {
        super(context);
    }

    public View getChildAt(@SuppressWarnings("unused") final int index) {
        return null;
    }

    public int getChildCount() {
        return 0;
    }
}
