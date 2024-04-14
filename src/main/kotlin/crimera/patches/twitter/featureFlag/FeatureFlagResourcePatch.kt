package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

object FeatureFlagResourcePatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        context.copyResources("twitter/settings", ResourceGroup("layout", "feature_flags_view.xml", "item_row.xml"))
    }
}