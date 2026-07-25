package app.crimera.patches.xlite.misc.extension

import app.morphe.patches.all.misc.extension.sharedExtensionPatch

internal val xLiteExtensionPatch =
    sharedExtensionPatch(
        listOf("shared", "xlite"),
        xLiteInitHook,
    )
