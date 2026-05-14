/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal object AuthorizeAppActivity : Fingerprint(
    definingClass = "Lcom/twitter/android/AuthorizeAppActivity;",
    name = "onCreate",
)

internal object SettingsFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters =
        listOf(
            string("pref_proxy"),
        ),
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/settings/SettingsStatus;",
    name = "load",
)

internal object UrlInterpreterActivity : Fingerprint(
    definingClass = "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;",
    name = "onCreate",
)
