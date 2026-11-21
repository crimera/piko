package app.morphe.extension.newx.ui;

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
        COPY_LINK,
        RESOLUTION,
        CHECKBOX_CHECKED,
        CHECKBOX_UNCHECKED,
        PLAY,
        GRID_VIEW,
        STAGGERED_VIEW,
        LIST_VIEW
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
            case RESOLUTION:
                drawResolutionIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case CHECKBOX_CHECKED:
                drawCheckboxChecked(canvas, cx, cy, size, strokeWidth);
                break;
            case CHECKBOX_UNCHECKED:
                drawCheckboxUnchecked(canvas, cx, cy, size, strokeWidth);
                break;
            case PLAY:
                drawPlayIcon(canvas, cx, cy, size);
                break;
            case GRID_VIEW:
                drawGridViewIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case STAGGERED_VIEW:
                drawStaggeredViewIcon(canvas, cx, cy, size, strokeWidth);
                break;
            case LIST_VIEW:
                drawListViewIcon(canvas, cx, cy, size, strokeWidth);
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

    private void drawResolutionIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        // Corner brackets read as a resolution/dimension frame.
        float half = size * 0.26f;
        float arm = size * 0.14f;
        canvas.drawLine(cx - half, cy - half, cx - half + arm, cy - half, paint);
        canvas.drawLine(cx - half, cy - half, cx - half, cy - half + arm, paint);
        canvas.drawLine(cx + half, cy - half, cx + half - arm, cy - half, paint);
        canvas.drawLine(cx + half, cy - half, cx + half, cy - half + arm, paint);
        canvas.drawLine(cx - half, cy + half, cx - half + arm, cy + half, paint);
        canvas.drawLine(cx - half, cy + half, cx - half, cy + half - arm, paint);
        canvas.drawLine(cx + half, cy + half, cx + half - arm, cy + half, paint);
        canvas.drawLine(cx + half, cy + half, cx + half, cy + half - arm, paint);

        // Diagonal expand arrow through the middle.
        canvas.drawLine(cx - size * 0.14f, cy + size * 0.14f, cx + size * 0.14f, cy - size * 0.14f, paint);
        path.reset();
        path.moveTo(cx + size * 0.06f, cy - size * 0.14f);
        path.lineTo(cx + size * 0.14f, cy - size * 0.14f);
        path.lineTo(cx + size * 0.14f, cy - size * 0.06f);
        canvas.drawPath(path, paint);
    }

    private void drawCheckboxChecked(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float radius = (size - strokeWidth) / 2f;

        // Filled circle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(iconColor);
        canvas.drawCircle(cx, cy, radius, paint);

        // High-contrast checkmark with sharp edges
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(isColorLight(iconColor) ? Color.BLACK : Color.WHITE);
        paint.setStrokeWidth(Math.max(strokeWidth * 1.1f, Theme.dpToPx(getContext(), 2f)));
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeMiter(4f);

        path.reset();
        path.moveTo(cx - radius * 0.48f, cy - radius * 0.02f);
        path.lineTo(cx - radius * 0.15f, cy + radius * 0.38f);
        path.lineTo(cx + radius * 0.50f, cy - radius * 0.35f);
        canvas.drawPath(path, paint);
    }

    private void drawCheckboxUnchecked(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float radius = (size - strokeWidth) / 2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(iconColor);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void drawPlayIcon(Canvas canvas, float cx, float cy, float size) {
        float triH = size * 0.56f;
        float triW = size * 0.48f;
        // Shift slightly right so the triangle is optically centered.
        float left = cx - triW * 0.38f;
        float right = left + triW;

        path.reset();
        path.moveTo(left, cy - triH / 2f);
        path.lineTo(right, cy);
        path.lineTo(left, cy + triH / 2f);
        path.close();

        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(Theme.dpToPx(getContext(), 1.5f));
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(path, paint);
    }

    private void drawGridViewIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float span = size * 0.68f;
        float gap = size * 0.14f;
        float cell = (span - gap) / 2f;
        float rx = Theme.dpToPx(getContext(), 2f);
        float startX = cx - span / 2f;
        float startY = cy - span / 2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        for (int row = 0; row < 2; row++) {
            float top = startY + row * (cell + gap);
            for (int col = 0; col < 2; col++) {
                float left = startX + col * (cell + gap);
                rectF.set(left, top, left + cell, top + cell);
                canvas.drawRoundRect(rectF, rx, rx, paint);
            }
        }
    }

    private void drawStaggeredViewIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float span = size * 0.68f;
        float gap = size * 0.14f;
        float colW = (span - gap) / 2f;
        float tallH = (span - gap) * 0.62f;
        float shortH = (span - gap) - tallH;
        float rx = Theme.dpToPx(getContext(), 2f);
        float leftCol = cx - span / 2f;
        float rightCol = leftCol + colW + gap;
        float topY = cy - span / 2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        // Left column: tall top, short bottom
        rectF.set(leftCol, topY, leftCol + colW, topY + tallH);
        canvas.drawRoundRect(rectF, rx, rx, paint);
        rectF.set(leftCol, topY + tallH + gap, leftCol + colW, topY + span);
        canvas.drawRoundRect(rectF, rx, rx, paint);

        // Right column: short top, tall bottom
        rectF.set(rightCol, topY, rightCol + colW, topY + shortH);
        canvas.drawRoundRect(rectF, rx, rx, paint);
        rectF.set(rightCol, topY + shortH + gap, rightCol + colW, topY + span);
        canvas.drawRoundRect(rectF, rx, rx, paint);
    }

    private void drawListViewIcon(Canvas canvas, float cx, float cy, float size, float strokeWidth) {
        float step = size * 0.24f;
        float dotX = cx - size * 0.30f;
        float dotRadius = Math.max(strokeWidth * 0.6f, Theme.dpToPx(getContext(), 1.2f));
        float lineStart = cx - size * 0.12f;
        float lineEnd = cx + size * 0.34f;

        for (int i = -1; i <= 1; i++) {
            float y = cy + i * step;
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(dotX, y, dotRadius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(lineStart, y, lineEnd, y, paint);
        }
    }

    private static boolean isColorLight(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.5;
    }
}
