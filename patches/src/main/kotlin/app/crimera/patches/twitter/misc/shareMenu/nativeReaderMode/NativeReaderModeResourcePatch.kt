package app.crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

internal val nativeReaderModeResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/settings",
                ResourceGroup("layout", "webview.xml")
            )
        }
    }
