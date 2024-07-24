package crimera.patches.twitter.misc.nativeDownloader.fingerprints

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch

@Patch(dependencies = [ResourceMappingPatch::class])
object NativeDownloaderResourcePatch : ResourcePatch() {
    internal var fileIcomingIcon = -1L

    override fun execute(context: ResourceContext) {
        fileIcomingIcon = ResourceMappingPatch["drawable", "ic_vector_incoming"]
    }
}