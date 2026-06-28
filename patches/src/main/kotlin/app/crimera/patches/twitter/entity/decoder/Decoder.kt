/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.decoder

import app.morphe.patcher.patch.bytecodePatch
import kotlin.properties.Delegates

var TIMELINE_ITEM_CLASS_NAME: String by Delegates.notNull()
    private set

var TWEET_ITEM_CLASS_NAME: String by Delegates.notNull()
    private set

val decoderEntity =
    bytecodePatch(
        description = "This patch is used hold class and field names that are commonly used",
    ) {
        execute {
            TIMELINE_ITEM_CLASS_NAME = TimelineItemDebugDialogInfoBuilderFingerprint.classDef.type

            TWEET_ITEM_CLASS_NAME = ContextualTweetPermaLinkBuilderFingerprint.classDef.type
        }
    }
