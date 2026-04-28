/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.extension.ExtensionHook

internal val twitterInitHook = ExtensionHook(
    fingerprint = Fingerprint(
        name = "onCreate",
        custom = { _, classDef ->
            classDef.superclass!!.contains("Landroid/app/Application;")
        }
    )
)
