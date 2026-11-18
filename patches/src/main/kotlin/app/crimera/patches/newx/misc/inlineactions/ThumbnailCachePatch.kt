package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.patch

private fun applyThumbnailCacheBridge() {
    val glideRuntime = resolveGlideThumbnailRuntimeOrNull()
    if (glideRuntime == null) {
        applyCoilThumbnailCachePatch()
        return
    }

    // The server-side switch can change after patching, so keep both cache
    // bridges available without patching the same helper twice.
    applyCoilThumbnailCachePatch(COIL_CACHED_THUMBNAIL_HELPER)
    applyGlideThumbnailCachePatch(glideRuntime)
}

internal val newXThumbnailCachePatch =
    patch(default = false) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        execute {
            applyThumbnailCacheBridge()
        }
    }
