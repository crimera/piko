/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.misc.customize.typeAheadResponse

import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.utsavrajput.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseTypeAheadResponseFingerprint : Fingerprint(
    definingClass = "JsonTypeaheadResponse\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

@Suppress("unused")
val customiseTypeAheadResponsePatch =
    bytecodePatch(
        name = "Customize search suggestions",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = CustomiseTypeAheadResponseFingerprint.method

            val instructions = method.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            method.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $CUSTOMISE_DESCRIPTOR;->typeAheadResponse(Lcom/twitter/model/json/search/JsonTypeaheadResponse;)Lcom/twitter/model/json/search/JsonTypeaheadResponse;
            move-result-object p1
            """,
            )
            enableSettings("typeaheadCustomisation")
        }
    }
