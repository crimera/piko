package app.crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

internal val nativeReaderModeResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/settings",
                ResourceGroup("layout", "webview.xml")
            )
        }
    }
