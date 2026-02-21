package app.morphe.extension.twitter.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.io.OutputStream;

public class ViewUtils {

    public static Bitmap viewToBitmap(View view) {
        return viewToBitmap(view, null);
    }

    public static Bitmap viewToBitmap(View view, Rect clipRect) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        int width = clipRect != null ? clipRect.width() : view.getWidth();
        int height = clipRect != null ? clipRect.height() : view.getHeight();
        
        app.morphe.extension.twitter.Utils.logger("Rendering bitmap: " + width + "x" + height + (clipRect != null ? " (clipping applied)" : ""));
        
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(resolveBackgroundColor(view));
        
        if (clipRect != null) {
            canvas.translate(-clipRect.left, -clipRect.top);
        }
        
        view.draw(canvas);
        return bitmap;
    }

    private static int resolveBackgroundColor(View view) {
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) return ((ColorDrawable) background).getColor();

        View root = view.getRootView();
        if (root != null && root.getBackground() instanceof ColorDrawable) {
            return ((ColorDrawable) root.getBackground()).getColor();
        }

        return Color.WHITE;
    }

    public static void saveBitmap(Bitmap bitmap, OutputStream os) throws Exception {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
    }

    public static void setScrollbarsVisible(View view, boolean visible) {
        view.setVerticalScrollBarEnabled(visible);
        view.setHorizontalScrollBarEnabled(visible);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setScrollbarsVisible(group.getChildAt(i), visible);
            }
        }
    }
}
