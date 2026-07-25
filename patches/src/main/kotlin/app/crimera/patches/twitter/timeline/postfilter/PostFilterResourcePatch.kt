/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.postfilter

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

internal val postFilterResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/postfilter",
                ResourceGroup(
                    "layout",
                    "post_filter_view.xml",
                    "post_filter_item.xml",
                ),
            )
        }
    }
