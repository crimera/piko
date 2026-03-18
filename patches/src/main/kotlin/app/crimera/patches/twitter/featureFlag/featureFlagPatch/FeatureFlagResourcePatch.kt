/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.featureFlag.featureFlagPatch

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

internal val featureFlagResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/settings",
                ResourceGroup(
                    "layout",
                    "feature_flags_view.xml",
                    "item_row.xml",
                    "search_item_row.xml",
                    "search_dialog.xml",
                )
            )
        }
    }
