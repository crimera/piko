package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch

/*
 * Temporary rollout gate for the X Lite media-loader migration. The 12.25 alpha
 * runs the Glide-backed renderer; the production targets currently use the
 * Coil-backed renderer. Keep this decision at patch time so only one bridge
 * mutates getCachedThumbnail.
 */
private const val GLIDE_ALPHA_VERSION_PREFIX = "12.25."
private const val ALPHA_CHANNEL_MARKER = "-alpha."

private fun useGlideThumbnailCache(versionName: String): Boolean =
    versionName.startsWith(GLIDE_ALPHA_VERSION_PREFIX) &&
        versionName.contains(ALPHA_CHANNEL_MARKER)

context(context: BytecodePatchContext)
private fun applyThumbnailCacheBridge() {
    if (useGlideThumbnailCache(context.packageMetadata.versionName)) {
        applyGlideThumbnailCachePatch()
    } else {
        applyCoilThumbnailCachePatch()
    }
}

internal val newXThumbnailCachePatch =
    bytecodePatch(default = false) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        execute {
            applyThumbnailCacheBridge()
        }
    }
