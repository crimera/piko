/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/** Draws the user's own image or GIF behind their profile header, from its top down to the action buttons. */
@SuppressWarnings("unused")
public final class ProfileCover {

    private static final String HEADER_LIST_ID = "profile_header_fixed_list";
    private static final String ACTION_BUTTONS_ID = "profile_header_actions_top_row";
    private static final String USERNAME_BUTTONS_ID = "action_bar_username_buttons_container";
    private static final String PICK_COVER_KEY = "piko_pref_pick_profile_cover";
    static final String EXTRA_ACCOUNT = "piko_profile_cover_account";
    private static final float SCRIM_OPACITY = 0.45f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 4f;
    // How far the image may be dragged past the area's edge, as a share of the area.
    private static final float OVERSCROLL = 0.4f;

    private static WeakReference<CoverButton> coverButton = new WeakReference<>(null);

    private ProfileCover() {
    }

    public static void apply(View headerView, Object profileInfoObject) {
        try {
            View container = findHeaderList(headerView);
            if (!(container instanceof ViewGroup)) return;

            String accountId = null;
            if (Pref.profileCover()) {
                ProfileInfo profileInfo = new ProfileInfo(profileInfoObject);
                if (profileInfo.isSelfProfile()) accountId = profileInfo.getUserData().getUserId();
            }
            updateCoverButton(headerView, accountId);
            updateCover((ViewGroup) container, accountId);
            if (accountId != null && coverOf((ViewGroup) container) != null) ProfileCoverGestures.install((ViewGroup) container);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to apply the profile cover", e);
        }
    }

    static CoverBackground coverOf(ViewGroup container) {
        Drawable background = container.getBackground();
        return background instanceof CoverBackground ? (CoverBackground) background : null;
    }

    private static void updateCover(ViewGroup container, String accountId) {
        Drawable current = container.getBackground();
        CoverBackground ours = current instanceof CoverBackground ? (CoverBackground) current : null;
        long stamp = accountId != null ? ProfileCoverStorage.stamp(accountId) : 0;

        if (stamp == 0) {
            if (ours != null) {
                container.setBackground(ours.original);
                ours.release();
            }
            return;
        }
        if (ours != null && ours.accountId.equals(accountId) && ours.stamp == stamp) return;

        Drawable cover = ProfileCoverStorage.decode(accountId);
        View buttonsRow = container.findViewById(idOf(container, ACTION_BUTTONS_ID));
        if (cover == null || buttonsRow == null) return;

        Drawable original = ours != null ? ours.original : current;
        if (ours != null) ours.release();
        container.setBackground(new CoverBackground(original, cover, accountId, stamp, container, buttonsRow));
    }

    /** The image button beside the account name: a tap picks the cover for this account, a long press removes it. */
    private static void updateCoverButton(View anchor, String accountId) {
        CoverButton existing = coverButton.get();
        if (existing != null && !existing.isAttachedToWindow()) existing = null;

        if (accountId == null) {
            if (existing != null && existing.getParent() instanceof ViewGroup) ((ViewGroup) existing.getParent()).removeView(existing);
            return;
        }
        if (existing != null) {
            existing.accountId = accountId;
            return;
        }

        View row = anchor.getRootView().findViewById(idOf(anchor, USERNAME_BUTTONS_ID));
        if (!(row instanceof LinearLayout) || ((LinearLayout) row).getOrientation() != LinearLayout.HORIZONTAL) return;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Dim.dp40, Dim.dp40);
        params.gravity = Gravity.CENTER_VERTICAL;
        params.setMarginStart(Dim.dp4);
        CoverButton created = new CoverButton(row.getContext(), accountId);
        ((LinearLayout) row).addView(created, params);
        coverButton = new WeakReference<>(created);
    }

    private static void pickCover(Context context, String accountId) {
        try {
            Intent intent = new Intent(context, RestorePrefActivity.class);
            intent.putExtra(PICK_COVER_KEY, true);
            intent.putExtra(EXTRA_ACCOUNT, accountId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to open the cover picker", e);
        }
    }

    private static void removeCover(View button, String accountId) {
        boolean removed = ProfileCoverStorage.delete(accountId);
        Utils.showToastShort(str(removed ? "piko_pref_delete_profile_cover_success" : "piko_pref_delete_profile_cover_none"));
        View header = button.getRootView().findViewById(idOf(button, HEADER_LIST_ID));
        if (header instanceof ViewGroup) updateCover((ViewGroup) header, accountId);
    }

    private static final class CoverButton extends ImageView {
        String accountId;

        CoverButton(Context context, String accountId) {
            super(context);
            this.accountId = accountId;
            UI.setThemedIcon(this, UI.DRAWABLE_PHOTO_ICON);
            setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);
            setContentDescription(str(PICK_COVER_KEY));
            setOnClickListener(v -> pickCover(v.getContext(), this.accountId));
            setOnLongClickListener(v -> {
                removeCover(v, this.accountId);
                return true;
            });
        }
    }

    private static int idOf(View view, String name) {
        return view.getResources().getIdentifier(name, "id", view.getContext().getPackageName());
    }

    private static View findHeaderList(View start) {
        int id = idOf(start, HEADER_LIST_ID);
        View node = start;
        while (node != null && node.getId() != id) {
            node = node.getParent() instanceof View ? (View) node.getParent() : null;
        }
        return node != null ? node : start.findViewById(id);
    }

    /** How far the content's center may move from the area's center. */
    private static float travel(float content, float area) {
        return Math.max(0f, (content - area) / 2f) + OVERSCROLL * area;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    static final class CoverBackground extends Drawable implements Drawable.Callback {
        final Drawable original;
        final String accountId;
        final long stamp;
        private final Drawable cover;
        private final View container;
        private final View buttonsRow;
        private final int fadePx;
        private final int featherPx;
        private final int surface = UI.getThemedColour("igds_color_primary_background");
        private final int clear = Color.argb(0, Color.red(surface), Color.green(surface), Color.blue(surface));
        private final Paint scrimPaint = new Paint();
        private final Paint fadePaint = new Paint();
        private final Paint[] featherPaints = new Paint[4];
        private final Shader[] featherShaders = new Shader[4];
        private final Matrix featherMatrix = new Matrix();
        private final Rect dst = new Rect();
        private final RectF image = new RectF();
        private int fadeFor = -1;
        // Zoom is relative to the size that fills the area; offsets are the image center's distance from the area's center, as a share of its size.
        private float zoom;
        private float offsetX;
        private float offsetY;

        CoverBackground(Drawable original, Drawable cover, String accountId, long stamp, View container, View buttonsRow) {
            this.original = original;
            this.cover = cover;
            this.accountId = accountId;
            this.stamp = stamp;
            this.container = container;
            this.buttonsRow = buttonsRow;
            this.fadePx = Dim.dp24;
            this.featherPx = Dim.dp32;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, ProfileCoverPref.zoom(accountId)));
            offsetX = clamp(ProfileCoverPref.offsetX(accountId), 1f);
            offsetY = clamp(ProfileCoverPref.offsetY(accountId), 1f);

            scrimPaint.setColor(Color.argb(Math.round(SCRIM_OPACITY * 255), Color.red(surface), Color.green(surface), Color.blue(surface)));
            for (int edge = 0; edge < 4; edge++) {
                boolean horizontal = edge % 2 == 0;
                featherShaders[edge] = new LinearGradient(0, 0, horizontal ? 1 : 0, horizontal ? 0 : 1, surface, clear, Shader.TileMode.CLAMP);
                featherPaints[edge] = new Paint();
                featherPaints[edge].setShader(featherShaders[edge]);
            }

            cover.setCallback(this);
            if (cover instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable animated = (AnimatedImageDrawable) cover;
                animated.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                animated.start();
            }
        }

        int zoneWidth() {
            return getBounds().width();
        }

        int zoneHeight() {
            int bottom = buttonsRow.getHeight();
            for (View view = buttonsRow; view != container && view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null) {
                bottom += view.getTop();
            }
            return Math.min(bottom, getBounds().height());
        }

        /** Moves the pinch midpoint from (oldX, oldY) to (newX, newY) while scaling by factor; px from the area's center. */
        void adjust(float oldX, float oldY, float newX, float newY, float factor) {
            int width = zoneWidth();
            int height = zoneHeight();
            int coverWidth = cover.getIntrinsicWidth();
            int coverHeight = cover.getIntrinsicHeight();
            if (width <= 0 || height <= 0 || coverWidth <= 0 || coverHeight <= 0) return;

            float base = Math.max(width / (float) coverWidth, height / (float) coverHeight);
            float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
            float growth = newZoom / zoom;
            float centerX = newX - (oldX - offsetX * width) * growth;
            float centerY = newY - (oldY - offsetY * height) * growth;

            zoom = newZoom;
            offsetX = clamp(centerX, travel(coverWidth * base * zoom, width)) / width;
            offsetY = clamp(centerY, travel(coverHeight * base * zoom, height)) / height;
            invalidateSelf();
        }


        void save() {
            ProfileCoverPref.save(accountId, zoom, offsetX, offsetY);
        }

        void release() {
            if (cover instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) cover).stop();
            cover.setCallback(null);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (original != null) {
                original.setBounds(bounds);
                original.draw(canvas);
            }
            int width = zoneWidth();
            int height = zoneHeight();
            int coverWidth = cover.getIntrinsicWidth();
            int coverHeight = cover.getIntrinsicHeight();
            if (width <= 0 || height <= 0 || coverWidth <= 0 || coverHeight <= 0) return;

            float scale = Math.max(width / (float) coverWidth, height / (float) coverHeight) * zoom;
            float scaledWidth = coverWidth * scale;
            float scaledHeight = coverHeight * scale;
            dst.set(bounds.left, bounds.top, bounds.left + width, bounds.top + height);
            float centerX = dst.exactCenterX() + clamp(offsetX * width, travel(scaledWidth, width));
            float centerY = dst.exactCenterY() + clamp(offsetY * height, travel(scaledHeight, height));
            image.set(centerX - scaledWidth / 2f, centerY - scaledHeight / 2f, centerX + scaledWidth / 2f, centerY + scaledHeight / 2f);

            canvas.save();
            canvas.clipRect(dst);
            cover.setBounds(Math.round(image.left), Math.round(image.top), Math.round(image.right), Math.round(image.bottom));
            cover.draw(canvas);
            canvas.drawRect(Math.max(image.left, dst.left), Math.max(image.top, dst.top),
                    Math.min(image.right, dst.right), Math.min(image.bottom, dst.bottom), scrimPaint);
            drawFeathers(canvas);

            if (fadeFor != height) {
                fadePaint.setShader(new LinearGradient(0, dst.bottom - fadePx, 0, dst.bottom, clear, surface, Shader.TileMode.CLAMP));
                fadeFor = height;
            }
            canvas.drawRect(dst.left, dst.bottom - fadePx, dst.right, dst.bottom, fadePaint);
            canvas.restore();
        }

        /** Where the image stops short of the area, its edge melts into the page instead of ending sharply. */
        private void drawFeathers(Canvas canvas) {
            float size = Math.min(featherPx, Math.min(image.width(), image.height()) / 2f);
            float left = Math.max(image.left, dst.left);
            float right = Math.min(image.right, dst.right);
            float top = Math.max(image.top, dst.top);
            float bottom = Math.min(image.bottom, dst.bottom);

            if (image.left > dst.left) {
                featherMatrix.setScale(size, 1f);
                featherMatrix.postTranslate(image.left, 0f);
                drawFeather(canvas, 0, image.left, top, image.left + size, bottom);
            }
            if (image.top > dst.top) {
                featherMatrix.setScale(1f, size);
                featherMatrix.postTranslate(0f, image.top);
                drawFeather(canvas, 1, left, image.top, right, image.top + size);
            }
            if (image.right < dst.right) {
                featherMatrix.setScale(-size, 1f);
                featherMatrix.postTranslate(image.right, 0f);
                drawFeather(canvas, 2, image.right - size, top, image.right, bottom);
            }
            if (image.bottom < dst.bottom) {
                featherMatrix.setScale(1f, -size);
                featherMatrix.postTranslate(0f, image.bottom);
                drawFeather(canvas, 3, left, image.bottom - size, right, image.bottom);
            }
        }

        private void drawFeather(Canvas canvas, int edge, float left, float top, float right, float bottom) {
            featherShaders[edge].setLocalMatrix(featherMatrix);
            featherPaints[edge].setShader(featherShaders[edge]);
            canvas.drawRect(left, top, right, bottom, featherPaints[edge]);
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            if (cover instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable animated = (AnimatedImageDrawable) cover;
                if (visible) animated.start();
                else animated.stop();
            }
            return changed;
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduleSelf(what);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
