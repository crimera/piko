package app.crimera.patches.xlite.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.extension.ExtensionHook

internal val xLiteInitHook =
    ExtensionHook(
        fingerprint =
            Fingerprint(
                name = "onCreate",
                custom = { _, classDef ->
                    classDef.superclass?.contains("Landroid/app/Application;") == true
                },
            ),
    )
