package crimera.patches.twitter.misc.integrations.fingerprints

import app.revanced.patches.shared.misc.integrations.BaseIntegrationsPatch.IntegrationsFingerprint

internal object InitFingerprint : IntegrationsFingerprint(
    strings = listOf("builderClass"),
    customFingerprint = { methodDef, _ ->
       methodDef.name == "onCreate"
    }
)