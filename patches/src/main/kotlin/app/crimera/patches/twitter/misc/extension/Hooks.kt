package app.crimera.patches.twitter.misc.extension

import app.revanced.patches.shared.misc.extension.extensionHook

internal val initHook =
    extensionHook {
        strings("Failed to start application for test.")
        custom { method, _ ->
            method.name == "onCreate"
        }
    }
