/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.roundedcorners

internal const val MEDIA = "Lcom/instagram/feed/media/Media;"
internal const val MEDIA_FRAME = "Lcom/instagram/ui/widget/framelayout/MediaFrameLayout;"
internal const val GESTURE_DETECTOR = "Landroid/view/GestureDetector;"
internal const val REBOUND_VIEW_PAGER = "Lcom/instagram/common/ui/widget/reboundviewpager/ReboundViewPager;"

internal const val ROUNDED_PACKAGE = "Lapp/morphe/extension/instagram/patches/feed/rounded"
internal const val ROUNDED_TARGETS = "$ROUNDED_PACKAGE/RoundedTargets;"
internal const val ROUNDED_MADE = "$ROUNDED_PACKAGE/RoundedCorners;->made(Ljava/lang/Object;)V"
internal const val PEEK_BUFFER = "$ROUNDED_PACKAGE/CarouselPeek;->buffer(Ljava/lang/Object;I)I"
