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
 * Click handler of the feed download button the patch builds into the Litho UFI component.
 * Instagram wraps the `ON_CLICK` prop as a Kotlin {@link Function1}, so the injected component
 * installs this implementation.
 *
 * <p>{@code mediaSource} is the feed row state rather than a `Media`: the builder only has a
 * 4-bit `iget` there, so the handler unwraps it at click time. The same state also yields the live
 * carousel index, so the download follows a swipe.
 *
 * <p>{@code context} must be the component's activity-scoped context; the application context
 * cannot host the download dialog.
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
        DownloadUtils.downloadPost(
                context,
                userSession,
                DownloadUtils.extractMedia(mediaSource),
                DownloadUtils.currentMediaIndex(mediaSource));
        return Unit.INSTANCE;
    }
}
