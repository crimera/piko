package app.morphe.extension.twitter.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.twitter.Utils;
import java.io.OutputStream;

public class ViewUtils {

    private static final int MAX_PIXELS = 40_000_000; // ~40MP safety limit

    public static Bitmap viewToBitmap(View view) {
        return viewToBitmap(view, null);
    }

    public static Bitmap viewToBitmap(View view, Rect clipRect) {
        if (view == null) return null;

        // Force measurement if view has no size
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                         View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        // Use clipRect for dimensions if provided, otherwise use view bounds.
        // We do NOT clamp to [0, width/height] because clipRect may target 
        // children that are partially scrolled off-screen (negative coordinates).
        int width = (clipRect != null) ? clipRect.width() : view.getWidth();
        int height = (clipRect != null) ? clipRect.height() : view.getHeight();

        if (width <= 0 || height <= 0) return null;

        float scale = 1.0f;
        if ((long) width * height > MAX_PIXELS) {
            scale = (float) Math.sqrt((double) MAX_PIXELS / ((long) width * height));
            width = Math.round(width * scale);
            height = Math.round(height * scale);
            Utils.logger("Scaling down bitmap to " + width + "x" + height + " (scale: " + scale + ")");
        }

        Utils.logger(String.format("Rendering bitmap: %dx%d%s", 
            width, height, clipRect != null ? " (clipping applied)" : ""));
        
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(resolveBackgroundColor(view));
            
            if (scale != 1.0f) {
                canvas.scale(scale, scale);
            }

            if (clipRect != null) {
                // Translate the canvas so that the top-left of the capture 
                // matches the top-left of the clipRect in the view's coordinate space.
                canvas.translate(-clipRect.left, -clipRect.top);
            }
            
            view.draw(canvas);
        } catch (OutOfMemoryError e) {
            Utils.logger("OOM while creating bitmap: " + e.getMessage());
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        } catch (Exception e) {
            Utils.logger("Error rendering bitmap: " + e.getMessage());
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        }
        return bitmap;
    }

    private static int resolveBackgroundColor(View view) {
        Drawable bg = view.getBackground();
        if (bg instanceof ColorDrawable) return ((ColorDrawable) bg).getColor();

        View root = view.getRootView();
        if (root != null && root.getBackground() instanceof ColorDrawable) {
            return ((ColorDrawable) root.getBackground()).getColor();
        }

        return Color.WHITE;
    }

    public static void saveBitmap(Bitmap bitmap, OutputStream os) throws Exception {
        if (bitmap == null || os == null) return;
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
    }

    public static void setScrollbarsVisible(View view, boolean visible) {
        if (view == null) return;
        
        view.setVerticalScrollBarEnabled(visible);
        view.setHorizontalScrollBarEnabled(visible);
        
        if (!(view instanceof ViewGroup)) return;
        
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setScrollbarsVisible(group.getChildAt(i), visible);
        }
    }
}
