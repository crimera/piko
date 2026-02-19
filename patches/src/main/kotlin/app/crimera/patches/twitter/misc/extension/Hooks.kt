package app.crimera.patches.twitter.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.shared.misc.extension.extensionHook

internal val initHook =
    extensionHook(
        fingerprint =
            Fingerprint(
                name = "onCreate",
                custom = { _, classDef ->
                    classDef.superclass!!.contains("Landroid/app/Application;")
                },
            ),
    )
