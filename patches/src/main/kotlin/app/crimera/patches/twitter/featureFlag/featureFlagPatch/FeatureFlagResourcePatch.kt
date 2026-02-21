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
