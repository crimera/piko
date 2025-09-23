package app.crimera.patches.twitter.misc.extension

import app.revanced.patches.shared.misc.extension.extensionHook

internal val initHook =
    extensionHook {
        strings("builderClass")
        custom { method, _ ->
            method.name == "onCreate"
        }
    }
