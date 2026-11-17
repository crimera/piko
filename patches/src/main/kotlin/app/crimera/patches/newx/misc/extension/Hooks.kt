package app.crimera.patches.newx.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patches.all.misc.extension.ExtensionHook

internal val newXInitHook =
    ExtensionHook(
        fingerprint =
            Fingerprint(
                name = "onCreate",
                // Application.onCreate must call the super method, which the instruction index finds.
                filters = listOf(methodCall(definingClass = "Landroid/app/Application;", name = "onCreate")),
                custom = { _, classDef ->
                    classDef.superclass?.contains("Landroid/app/Application;") == true
                },
            ),
    )
