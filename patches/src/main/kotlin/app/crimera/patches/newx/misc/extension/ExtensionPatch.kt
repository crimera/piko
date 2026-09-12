package app.crimera.patches.newx.misc.extension

import app.morphe.patches.all.misc.extension.sharedExtensionPatch

internal val newXExtensionPatch =
    sharedExtensionPatch(
        listOf("shared", "newx"),
        newXInitHook,
    )
