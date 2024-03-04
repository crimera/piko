package crimera.patches.foobar.layout.redesign

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

@Patch(
    name = "Redesign",
    description = "Redesign some parts of the ui",
    compatiblePackages = [CompatiblePackage("com.foobar2000.foobar2000")]
)
object RedesignPlayback : ResourcePatch() {
    override fun execute(context: ResourceContext) {
        context.copyResources(
            "foobar",
            ResourceGroup("layout-v17", "fragment_browse.xml"),
            ResourceGroup("layout", "fragment_playback.xml"),
            ResourceGroup("layout", "fragment_browse.xml"),
            ResourceGroup("layout", "item_sectionheader.xml"),
            ResourceGroup("layout", "item_browse.xml"),
            ResourceGroup("drawable", "cog.png"),
            ResourceGroup("drawable", "search.png"),
            ResourceGroup("drawable", "next.png"),
            ResourceGroup("drawable", "prev.png"),
            ResourceGroup("drawable", "pause.png"),
            ResourceGroup("drawable", "play.png"),
        )
    }
}