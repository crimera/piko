package app.crimera.patches.twitter.featureFlag

import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

internal val featureFlagResourcePatch =
    resourcePatch {
        execute {
//            mergeXmlResources("twitter/settings", "$it/arrays.xml")
            copyResources(
                "twitter/settings",
                ResourceGroup(
                    "layout",
                    "feature_flags_view.xml",
                    "item_row.xml",
                    "search_item_row.xml",
                    "search_dialog.xml",
                ),
            )
        }
    }
