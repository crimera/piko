package app.morphe.extension.xlite.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Custom vector icon view drawn programmatically on Canvas.
 */
public class IconView extends View {

    public enum IconType {
        IMAGE,
        VIDEO,
        GIF,
        DOWNLOAD,
        CLOSE,
        COPY_LINK
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    private IconType iconType = IconType.IMAGE;
    private int iconColor = Color.WHITE;

    public IconView(Context context) {
        super(context);
    }

    public IconView(Context context, IconType type, int color) {
        super(context);
        this.iconType = type;
        this.iconColor = color;
    }

    public void setIconType(IconType type) {
        this.iconType = type;
        invalidate();
    }

    public void setIconColor(int color) {
        this.iconColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float size = Math.min(w, h);
        float strokeWidth = Theme.dpToPx(getContext(), 2f);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(iconColor);

        switch (iconType) {
            case IMAGE:
                drawImageIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case VIDEO:
                drawVideoIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case GIF:
                drawGifIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case DOWNLOAD:
                drawDownloadIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case CLOSE:
                drawCloseIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case COPY_LINK:
                drawCopyLinkIcon(canvas, cx, cy, size, strokeWidth);
                break;
        }
    }

    private void drawImageIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float rectW = size * 0.72f;
        float rectH = size * 0.58f;
        float rx = Theme.dpToPx(getContext(), 3f);
        rectF.set(cx - rectW / 2f, cy - rectH / 2f, cx + rectW / 2f, cy + rectH / 2f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawRoundRect(rectF, rx, rx, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - rectW * 0.22f, cy - rectH * 0.2f, size * 0.08f, paint);

        path.reset();
        path.moveTo(cx - rectW * 0.45f, cy + rectH * 0.45f);
        path.lineTo(cx - rectW * 0.15f, cy - rectH * 0.1f);
        path.lineTo(cx + rectW * 0.1f, cy + rectH * 0.2f);
        path.lineTo(cx + rectW * 0.28f, cy - rectH * 0.02f);
        path.lineTo(cx + rectW * 0.45f, cy + rectH * 0.45f);

        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, paint);
    }

    private void drawVideoIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float rectW = size * 0.52f;
        float rectH = size * 0.48f;
        float rx = Theme.dpToPx(getContext(), 3f);
        float left = cx - size * 0.36f;
        rectF.set(left, cy - rectH / 2f, left + rectW, cy + rectH / 2f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawRoundRect(rectF, rx, rx, paint);

        path.reset();
        float lensLeft = left + rectW + Theme.dpToPx(getContext(), 2f);
        float lensRight = cx + size * 0.36f;
        path.moveTo(lensLeft, cy - size * 0.08f);
        path.lineTo(lensRight, cy - size * 0.2f);
        path.lineTo(lensRight, cy + size * 0.2f);
        path.lineTo(lensLeft, cy + size * 0.08f);
        path.close();

        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawPath(path, paint);
    }

    private void drawGifIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float rectW = size * 0.78f;
        float rectH = size * 0.54f;
        float rx = Theme.dpToPx(getContext(), 4f);
        rectF.set(cx - rectW / 2f, cy - rectH / 2f, cx + rectW / 2f, cy + rectH / 2f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawRoundRect(rectF, rx, rx, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size * 0.32f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("GIF", cx, baseline, paint);
    }

    private void drawDownloadIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float topY = cy - size * 0.3f;
        float arrowBottomY = cy + size * 0.12f;
        float arrowWidth = size * 0.22f;

        canvas.drawLine(cx, topY, cx, arrowBottomY, paint);

        path.reset();
        path.moveTo(cx - arrowWidth, arrowBottomY - arrowWidth);
        path.lineTo(cx, arrowBottomY);
        path.lineTo(cx + arrowWidth, arrowBottomY - arrowWidth);
        canvas.drawPath(path, paint);

        float trayWidth = size * 0.65f;
        float trayY = cy + size * 0.3f;
        canvas.drawLine(cx - trayWidth / 2f, trayY, cx + trayWidth / 2f, trayY, paint);
    }

    private void drawCloseIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float offset = size * 0.22f;
        canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, paint);
        canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, paint);
    }

    private void drawCopyLinkIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        float scale = size / 24f;
        float left = cx - 12f * scale;
        float top = cy - 12f * scale;

        // Left loop: M9 17 H7 A5 5 0 0 1 7 7 h2
        path.reset();
        path.moveTo(left + 9f * scale, top + 17f * scale);
        path.lineTo(left + 7f * scale, top + 17f * scale);
        rectF.set(left + 2f * scale, top + 7f * scale, left + 12f * scale, top + 17f * scale);
        path.arcTo(rectF, 90f, 180f);
        path.lineTo(left + 9f * scale, top + 7f * scale);
        canvas.drawPath(path, paint);

        // Right loop: M15 7 h2 A5 5 0 1 1 17 17 h-2
        path.reset();
        path.moveTo(left + 15f * scale, top + 7f * scale);
        path.lineTo(left + 17f * scale, top + 7f * scale);
        rectF.set(left + 12f * scale, top + 7f * scale, left + 22f * scale, top + 17f * scale);
        path.arcTo(rectF, 270f, 180f);
        path.lineTo(left + 15f * scale, top + 17f * scale);
        canvas.drawPath(path, paint);

        // Center connecting line: line x1="8" x2="16" y1="12" y2="12"
        canvas.drawLine(left + 8f * scale, top + 12f * scale, left + 16f * scale, top + 12f * scale, paint);
    }
}
