/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val AUTHORIZE_APP_ACTIVITY_CLASS = "Lcom/twitter/android/AuthorizeAppActivity;"
internal const val URL_INTERPRETER_ACTIVITY_CLASS = "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;"

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

internal object AuthorizeAppActivityFingerprint : Fingerprint(
    definingClass = AUTHORIZE_APP_ACTIVITY_CLASS,
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V"
)

internal object AuthorizeAppActivityVirtualFingerprint : Fingerprint(
    definingClass = $$"Lcom/twitter/android/AuthorizeAppActivity$c",
    name = "onCreate",
    parameters = listOf(AUTHORIZE_APP_ACTIVITY_CLASS, "Landroid/os/Bundle;"),
    returnType = "V"
)

internal object SettingsFragmentFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        string("pref_proxy"),
        opcode(Opcode.FILLED_NEW_ARRAY_RANGE),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    ),
)

internal object SettingsPreferenceFingerprint : Fingerprint(
    classFingerprint = SettingsFragmentFingerprint,
    parameters = listOf("Landroidx/preference/Preference;"),
    returnType = "Z",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "Landroidx/preference/Preference;",
            type = "Ljava/lang/String;"
        )
    ),
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/settings/SettingsStatus;",
    name = "load",
)

internal object UrlInterpreterActivityFingerprint : Fingerprint(
    definingClass = URL_INTERPRETER_ACTIVITY_CLASS,
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V"
)

internal object UrlInterpreterActivityVirtualFingerprint : Fingerprint(
    definingClass = $$"Lcom/twitter/deeplink/implementation/UrlInterpreterActivity$c",
    name = "onCreate",
    parameters = listOf(URL_INTERPRETER_ACTIVITY_CLASS, "Landroid/os/Bundle;"),
    returnType = "V"
)
