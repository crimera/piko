package crimera.patches.twitter.misc.integrations

import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.integrations.BaseIntegrationsPatch
import crimera.patches.twitter.misc.integrations.fingerprints.InitFingerprint

@Patch(
    requiresIntegrations = true
)
object IntegrationsPatch: BaseIntegrationsPatch(
    setOf(InitFingerprint)
)