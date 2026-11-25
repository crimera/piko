/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed.rounded;

import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class RoundedFrames {
    private static final class Kept {
        final ViewOutlineProvider provider;
        final boolean clipped;
        float radius;

        Kept(View view) {
            provider = view.getOutlineProvider();
            clipped = view.getClipToOutline();
        }
    }

    private static final class Rounded extends ViewOutlineProvider {
        final float radius;

        Rounded(float radius) {
            this.radius = radius;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            Rect box = new Rect(0, 0, view.getWidth(), view.getHeight());
            outline.setRoundRect(box, radius);
        }
    }

    private final Map<View, Kept> kept = new WeakHashMap<>();

    public boolean round(View view, float radius) {
        Kept before = kept.get(view);
        if (before == null) {
            before = new Kept(view);
            kept.put(view, before);
        } else if (before.radius == radius) {
            return false;
        }
        before.radius = radius;
        view.setOutlineProvider(new Rounded(radius));
        view.setClipToOutline(true);
        return true;
    }

    public void putBack() {
        for (Map.Entry<View, Kept> entry : new ArrayList<>(kept.entrySet())) {
            View view = entry.getKey();
            if (view == null) continue;
            view.setOutlineProvider(entry.getValue().provider);
            view.setClipToOutline(entry.getValue().clipped);
        }
        kept.clear();
    }

    public boolean isEmpty() {
        return kept.isEmpty();
    }
}
