package app.crimera.patches.twitter.misc.settings

import app.revanced.patcher.fingerprint

internal val authorizeAppActivity =
    fingerprint {
        custom { method, _ ->
            method.definingClass == "Lcom/twitter/android/AuthorizeAppActivity;" &&
                method.name == "onCreate"
        }
    }

internal val settingsFingerprint =
    fingerprint {
        returns("V")
        strings("pref_proxy")
        custom { method, _ -> method.name == "<clinit>" }
    }

internal val settingsStatusLoadFingerprint =
    fingerprint {
        custom { method, _ ->
            method.definingClass.endsWith("Lapp/revanced/extension/twitter/settings/SettingsStatus;") &&
                method.name == "load"
        }
    }

internal val urlInterpreterActivity =
    fingerprint {
        custom { method, _ ->
            method.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;" &&
                method.name == "onCreate"
        }
    }
