package app.crimera.patches.instagram.misc.extension

import app.revanced.patches.instagram.misc.extension.hooks.applicationInitHook
import app.revanced.patches.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch = sharedExtensionPatch(
    "instagram",
    applicationInitHook,
)
