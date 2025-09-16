package crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class NativeReaderModeResourcePatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        context.copyResources("twitter/readermode", ResourceGroup("raw", "reader_mode.html"))
    }
}