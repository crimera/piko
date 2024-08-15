package crimera.patches.twitter.misc.extensions

import app.revanced.patches.shared.misc.extensions.extensionsHook

internal val initHook = extensionsHook {
    strings("builderClass")
    custom { method, _ ->
        method.name == "onCreate"
    }
}