package app.morphe.extension.twitter.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

public class ViewUtils {

    /**
     * Convert any Android View to Bitmap
     * Works with: LinearLayout, ConstraintLayout, CardView, etc
     */
    public static Bitmap viewToBitmap(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            // Force layout if not measured yet
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        Bitmap bitmap = Bitmap.createBitmap(
            view.getWidth(),
            view.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);  // Background
        view.draw(canvas);

        return bitmap;
    }

    /**
     * Convert a portion of a View to Bitmap using a clip rect in the view's coordinates.
     */
    public static Bitmap viewToBitmap(View view, Rect clipRect) {
        if (clipRect == null) return viewToBitmap(view);

        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }

        int width = Math.max(1, clipRect.width());
        int height = Math.max(1, clipRect.height());

        Bitmap bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        canvas.translate(-clipRect.left, -clipRect.top);
        view.draw(canvas);

        return bitmap;
    }

    /**
     * Save Bitmap to PNG file
     */
    public static File saveBitmap(Bitmap bitmap, File outputFile) throws Exception {
        outputFile.getParentFile().mkdirs();

        FileOutputStream fos = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
        fos.flush();
        fos.close();

        return outputFile;
    }

    /**
     * Save Bitmap to JPEG (smaller file)
     */
    public static File saveBitmapJpeg(Bitmap bitmap, File outputFile, int quality) throws Exception {
        outputFile.getParentFile().mkdirs();

        FileOutputStream fos = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        fos.flush();
        fos.close();

        return outputFile;
    }
}
