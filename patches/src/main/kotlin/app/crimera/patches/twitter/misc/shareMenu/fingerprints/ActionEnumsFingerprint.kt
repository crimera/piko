/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.crimera.utils.InitMethod
import app.crimera.utils.indexOfLastFilledNewArrayRange
import app.crimera.utils.indexOfLastNewInstance
import app.crimera.utils.instructionToString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patcher.util.smali.toInstructions
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc

internal object ActionEnumsFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/core",
    strings = listOf("None", "Favorite", "Retweet"),
)

context(patchContext: BytecodePatchContext)
fun addAction(name: String): String {
    val field =
        ActionEnumsFingerprint.classDef.fields
            .lastOrNull()
            ?.toMutable()
            ?: throw PatchException("Failed to find any fields in ${ActionEnumsFingerprint.definingClass}")
    field.name = name
    ActionEnumsFingerprint.classDef.fields.add(field)

    val oldInit = ActionEnumsFingerprint.classDef.methods.firstOrNull { it.name == "<clinit>" }
        ?: throw PatchException("Failed to find <clinit> in ${ActionEnumsFingerprint.definingClass}")

    val oldInitInstructions = oldInit.implementation?.instructions?.toMutableList()
        ?: throw PatchException("<clinit> has no implementation in ${ActionEnumsFingerprint.definingClass}")

    val lastNewInstanceIdx = oldInitInstructions.indexOfLastNewInstance
    if (lastNewInstanceIdx == -1) throw PatchException("Failed to find NEW_INSTANCE in <clinit> of ${ActionEnumsFingerprint.definingClass}")

    val lastNewInstance = (oldInitInstructions[lastNewInstanceIdx] as Instruction21c)
    val lastNewInstanceReg = lastNewInstance.registerA
    val stringReg = lastNewInstanceReg + 1
    val constReg = stringReg + 1

    val fieldCall = "${lastNewInstance.reference}->$name:${lastNewInstance.reference}"

    val newArrayPos = oldInitInstructions.indexOfLastFilledNewArrayRange
    if (newArrayPos == -1) throw PatchException("Failed to find FILLED_NEW_ARRAY_RANGE in <clinit> of ${ActionEnumsFingerprint.definingClass}")

    val arr = oldInitInstructions[newArrayPos] as Instruction3rc
    val arrStartReg = arr.startRegister
    val arrEndReg = (arrStartReg + arr.registerCount)

    oldInitInstructions[newArrayPos] =
        """
        filled-new-array/range {v$arrStartReg .. v${arrEndReg + arrStartReg}}, [${lastNewInstance.reference}
        """.trimIndent().toInstruction()

    oldInitInstructions.add(
        newArrayPos,
        """
        sget-object v$arrEndReg, $fieldCall
        """.trimIndent().toInstruction(),
    )

    """
    new-instance v$lastNewInstanceReg, ${lastNewInstance.reference}
    const-string v$stringReg, "$name"
    const/16 v$constReg, ${arrEndReg - arrStartReg}
    invoke-direct {v$lastNewInstanceReg, v$stringReg, v$constReg}, Ljava/lang/Enum;-><init>(Ljava/lang/String;I)V
    sput-object v$lastNewInstanceReg, $fieldCall
    """.trimIndent().toInstructions().forEach {
        oldInitInstructions.add(lastNewInstanceIdx, it)
    }

    val instructions =
        oldInitInstructions.map { ins ->
            instructionToString(ins)
        }

    val newInitImplementation = MethodImplementationBuilder(arrEndReg + 1)
    instructions.forEach { ins ->
        newInitImplementation.addInstruction(ins.toInstruction())
    }

    val newInit =
        InitMethod(
            validator = { oldInit.validateReference() },
            compare = { oldInit.compareTo(it) },
            definingClass = oldInit.definingClass,
            name = oldInit.name,
            parameterTypes = oldInit.parameterTypes,
            returnType = oldInit.returnType,
            annotations = oldInit.annotations,
            accessFlags = oldInit.accessFlags,
            hiddenApiRestrictions = oldInit.hiddenApiRestrictions,
            parameters = oldInit.parameters,
            implementation = newInitImplementation.methodImplementation,
        )

    val actionsEnumClass = ActionEnumsFingerprint.classDef
    actionsEnumClass.methods.removeIf { it.name == "<clinit>" }
    actionsEnumClass.methods.add(MutableMethod(newInit))
    return fieldCall
}
