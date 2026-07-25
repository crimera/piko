package app.crimera.patches.xlite.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

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
            string("onClick"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Landroidx/compose/runtime/Composer;",
                parameters = listOf("I"),
                returnType = "Landroidx/compose/runtime/Composer;",
            ),
        ),
)
