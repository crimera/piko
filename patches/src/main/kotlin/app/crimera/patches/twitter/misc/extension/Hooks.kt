package app.crimera.patches.twitter.misc.extension

import app.revanced.patches.shared.misc.extension.extensionHook

internal val initHook =
    extensionHook {
        custom { method, classDef ->
            method.name == "onCreate" && classDef.superclass!!.contains("Landroid/app/Application;")
        }
    }
