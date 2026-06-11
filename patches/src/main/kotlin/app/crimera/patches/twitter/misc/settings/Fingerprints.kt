/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val URL_INTERPRETER_ACTIVITY_CLASS = "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity"

internal object ApplicationFingerprint : Fingerprint(
    name = "attachBaseContext",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Landroid/content/Context;"),
            returnType = "V"
        )
    ),
    custom = { _, classDef ->
        classDef.superclass == "Lcom/twitter/app/TwitterApplication;"
    }
)

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

internal object UrlInterpreterActivityFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("$URL_INTERPRETER_ACTIVITY_CLASS;") && method.name == "onCreate"
    },
)

internal object UrlInterpreterActivityPairIPFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.contains("$URL_INTERPRETER_ACTIVITY_CLASS\$c") && method.name == "onCreate"
    },
)
