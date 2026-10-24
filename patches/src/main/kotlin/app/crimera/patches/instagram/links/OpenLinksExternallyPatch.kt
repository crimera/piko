/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object InAppBrowserFunctionFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("Tracking.ARG_CLICK_SOURCE"),
        methodCall(smali = "Landroid/os/BaseBundle;->putString(Ljava/lang/String;Ljava/lang/String;)V"),
        // The URL field is read after the tracking Bundle has been populated.
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_OBJECT_FROM16, MatchAfterImmediately()),
        methodCall(parameters = listOf("Ljava/lang/String;"), returnType = "Landroid/net/Uri;", opcode = Opcode.INVOKE_STATIC_RANGE),
    ),
)

context(patchContext: BytecodePatchContext)
private fun externalBrowserResult(resultType: String): FieldReference {
    val resultClass = patchContext.classDefBy(resultType)
    if (resultClass.superclass != "Ljava/lang/Enum;") {
        throw PatchException("Expected a browser launch result enum")
    }
    val result = Fingerprint(
        definingClass = resultType,
        name = "<clinit>",
        filters = listOf(
            string("EXTERNAL_BROWSER"),
            methodCall(
                definingClass = resultType,
                name = "<init>",
                parameters = listOf("Ljava/lang/String;", "I"),
                opcode = Opcode.INVOKE_DIRECT,
                location = MatchAfterWithin(3),
            ),
            fieldAccess(
                definingClass = resultType,
                type = resultType,
                opcode = Opcode.SPUT_OBJECT,
                location = MatchAfterImmediately(),
            ),
        ),
    ).matchSingle()
    val name = result.instructionMatches[0]
    val constructor = result.instructionMatches[1]
    val store = result.instructionMatches[2]
    val nameRegister = name.instruction.registersUsed.single()
    val constructorRegisters = constructor.instruction.registersUsed
    val field = store.instruction.getReference<FieldReference>()!!
    if (constructorRegisters.size != 3 || constructorRegisters[1] != nameRegister ||
        store.instruction.registersUsed != listOf(constructorRegisters[0]) ||
        (name.index + 1 until constructor.index).any { index ->
            val instruction = result.method.getInstruction(index)
            instruction.opcode.setsRegister() && instruction.registersUsed.firstOrNull() == nameRegister
        } || resultClass.fields.none {
            it.name == field.name && it.type == field.type &&
                AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags)
        }
    ) throw PatchException("Expected the external browser result to retain its enum name")
    return field
}

@Suppress("unused")
val openLinksExternallyPatch =
    bytecodePatch(
        name = "Open links externally",
        description = "Changes links to always open in your external browser, instead of the in-app browser.",
        default = true,
    ) {

        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            InAppBrowserFunctionFingerprint.matchSingle().let {
                val urlMove = it.instructionMatches[3]
                val urlResultObjIndex = urlMove.index
                val moveRegisters = urlMove.instruction.registersUsed
                val fieldRegisters = it.instructionMatches[2].instruction.registersUsed
                val parseRegisters = it.instructionMatches[4].instruction.registersUsed
                if (moveRegisters.size != 2 || fieldRegisters.size != 2 ||
                    moveRegisters[1] != fieldRegisters[0] || parseRegisters != listOf(moveRegisters[0])) {
                    throw PatchException("Expected the browser URL field to reach the URI parser")
                }

                it.method.apply {
                    val urlRegister = moveRegisters[0]
                    val freeRegister = findFreeRegister(urlResultObjIndex + 1)
                    val handledResult = externalBrowserResult(returnType)

                    addInstructionsWithLabels(
                        urlResultObjIndex + 1,
                        """
                        invoke-static/range { v$urlRegister .. v$urlRegister }, ${Constants.LINKS_DESCRIPTOR}->openExternally(Ljava/lang/String;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        sget-object v$freeRegister, $handledResult
                        return-object v$freeRegister
                    """,
                        ExternalLabel("piko", getInstruction(urlResultObjIndex + 1)),
                    )

                    enableSettings("openLinksExternally")
                }
            }
        }
    }
