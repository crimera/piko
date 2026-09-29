/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.content.Context;

import com.instagram.common.session.UserSession;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Litho `onClick` handler for the feed download button injected into the UFI component at patch
 * time. Instagram wraps the `ON_CLICK` prop as a Kotlin {@link Function1}
 * (`LX/01kJ;->A0N`), so the injected component installs this implementation.
 *
 * <p>{@code mediaSource} is the row state (`LX/01Tx`), not a `Media`: at the component builder
 * every low register is live, and the state's single `Media` field can only be read there with a
 * 4-bit `iget`. The handler unwraps it at click time instead.
 *
 * <p>{@code context} is the component's activity-scoped context. The shared application context
 * cannot host the download dialog (`WindowManager$BadTokenException`).
 */
public final class FeedDownloadClickFunction implements Function1<Object, Object> {
    private final Context context;
    private final UserSession userSession;
    private final Object mediaSource;

    public FeedDownloadClickFunction(Context context, UserSession userSession, Object mediaSource) {
        this.context = context;
        this.userSession = userSession;
        this.mediaSource = mediaSource;
    }

    @Override
    public Object invoke(Object clickEvent) {
        Object media = DownloadUtils.extractMedia(mediaSource);
        DownloadUtils.downloadPost(
                context,
                userSession,
                media == null ? mediaSource : media,
                0);
        return Unit.INSTANCE;
    }
}
