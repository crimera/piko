/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
*/

package app.crimera.patches.instagram.misc.extension

import app.crimera.patches.instagram.misc.extension.hooks.applicationInitHook
import app.morphe.shared.misc.extension.sharedExtensionPatch

val sharedExtensionPatch =
    sharedExtensionPatch(
        "instagram",
        applicationInitHook,
    )
