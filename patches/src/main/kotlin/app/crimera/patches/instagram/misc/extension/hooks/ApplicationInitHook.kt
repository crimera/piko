package app.crimera.patches.instagram.misc.extension.hooks

import app.revanced.patches.shared.misc.extension.extensionHook

internal val applicationInitHook = extensionHook {
    custom { method, classDef ->
        method.name == "onCreate" && classDef.endsWith("/InstagramAppShell;")
    }
}
