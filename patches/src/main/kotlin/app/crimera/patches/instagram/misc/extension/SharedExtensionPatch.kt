package app.crimera.patches.instagram.misc.extension

import app.crimera.patches.instagram.misc.extension.hooks.applicationInitHook
import app.morphe.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch =
    sharedExtensionPatch(
        "instagram",
        applicationInitHook,
    )
