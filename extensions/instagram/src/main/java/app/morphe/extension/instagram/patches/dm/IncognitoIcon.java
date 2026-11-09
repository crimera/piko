/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** The incognito icon (a hat over a pair of glasses), drawn in code in one colour. */
final class IncognitoIcon extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hat = new Path();
    private final Path brim = new Path();
    private final Path bridge = new Path();
    private final RectF lens = new RectF();

    IncognitoIcon(int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        float size = Math.min(getBounds().width(), getBounds().height());
        float left = getBounds().left + (getBounds().width() - size) / 2f;
        float top = getBounds().top + (getBounds().height() - size) / 2f;

        // The icon is laid out on a 1 x 1 square and scaled to the bounds.
        hat.rewind();
        hat.moveTo(left + 0.30f * size, top + 0.07f * size);
        hat.lineTo(left + 0.46f * size, top + 0.15f * size);
        hat.lineTo(left + 0.70f * size, top + 0.06f * size);
        hat.lineTo(left + 0.78f * size, top + 0.40f * size);
        hat.lineTo(left + 0.22f * size, top + 0.40f * size);
        hat.close();
        canvas.drawPath(hat, fill);

        brim.rewind();
        brim.moveTo(left + 0.10f * size, top + 0.60f * size);
        brim.quadTo(left + 0.50f * size, top + 0.36f * size, left + 0.90f * size, top + 0.60f * size);
        brim.quadTo(left + 0.50f * size, top + 0.52f * size, left + 0.10f * size, top + 0.60f * size);
        brim.close();
        canvas.drawPath(brim, fill);

        stroke.setStrokeWidth(0.075f * size);
        float radius = 0.155f * size;
        for (float center : new float[]{0.31f, 0.69f}) {
            lens.set(left + center * size - radius, top + 0.83f * size - radius,
                    left + center * size + radius, top + 0.83f * size + radius);
            canvas.drawOval(lens, stroke);
        }
        bridge.rewind();
        bridge.moveTo(left + 0.455f * size, top + 0.78f * size);
        bridge.quadTo(left + 0.50f * size, top + 0.74f * size, left + 0.545f * size, top + 0.78f * size);
        canvas.drawPath(bridge, stroke);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); stroke.setColorFilter(filter); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
