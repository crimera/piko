package app.crimera.patches.xlite.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

internal object ComposeSettingsBasicItemFingerprint : Fingerprint(
    returnType = "V",
    parameters =
        listOf(
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "L",
            "Lkotlin/jvm/functions/Function0;",
            "L",
            "J",
            "J",
            "Landroidx/compose/runtime/Composer;",
            "I",
            "I",
        ),
    filters =
        listOf(
            string("title"),
            methodCall(
                parameters = listOf("I"),
                returnType = "Landroidx/compose/runtime/Composer;",
            ),
        ),
)
