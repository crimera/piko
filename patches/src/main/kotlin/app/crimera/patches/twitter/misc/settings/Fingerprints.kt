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
