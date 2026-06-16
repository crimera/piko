/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.crimera.patches.twitter.utils.is_11_92_or_greater
import app.crimera.utils.InitMethod
import app.crimera.utils.indexOfLastFilledNewArrayRange
import app.crimera.utils.indexOfLastNewInstance
import app.crimera.utils.instructionToString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc

internal object ActionEnumsFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/core",
    strings = listOf("None", "Favorite", "Retweet"),
)

context(patchContext: BytecodePatchContext)
fun addActionV1(name: String): String {
    val classDef = ActionEnumsFingerprint.classDef
    val classFields = classDef.fields
    val classMethods = classDef.methods

    val field =
        classFields
            .last()
            .toMutable()
    field.name = name
    classFields.add(field)

    val methodName = "<clinit>"

    val oldInit = classMethods.first { it.name == methodName }
    val oldInitInstructions = oldInit.implementation!!.instructions.toMutableList()

    val lastNewInstance = (oldInitInstructions[oldInitInstructions.indexOfLastNewInstance] as Instruction21c)
    val lastNewInstanceReg = lastNewInstance.registerA
    val stringReg = lastNewInstanceReg + 1
    val constReg = stringReg + 1

    val newArrayPos = oldInitInstructions.indexOfLastFilledNewArrayRange

    val fieldCall = "${lastNewInstance.reference}->$name:${lastNewInstance.reference}"

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
        oldInitInstructions.add(oldInitInstructions.indexOfLastNewInstance, it)
    }

    val instructions =
        oldInitInstructions.map { ins ->
            instructionToString(ins)
        }

    val newInitImplementation = MethodImplementationBuilder(arrEndReg + 1)
    instructions.forEach { ins ->
        newInitImplementation.addInstruction(ins.toInstruction())
    }

    // TODO: clean this up, we should just pass a method here and make this into an improved MutableMethod class
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

    classMethods.removeIf { it.name == methodName }
    classMethods.add(MutableMethod(newInit))
    return fieldCall
}

context(patchContext: BytecodePatchContext)
fun addActionV2(name: String): String {
    val classDef = ActionEnumsFingerprint.classDef
    val classFields = classDef.fields
    val classMethods = classDef.methods

    val fieldCall = "$classDef->$name:$classDef"

    val field =
        classFields
            .last()
            .toMutable()
    field.name = name
    classFields.add(field)

    val methodName = "\$values"
    val oldValues = classMethods.first { it.name == methodName }

    val oldValuesInstructions = oldValues.implementation!!.instructions.toMutableList()

    val newArrayPos = oldValuesInstructions.indexOfLastFilledNewArrayRange

    val arr = oldValuesInstructions[newArrayPos] as Instruction3rc
    val arrStartReg = arr.startRegister
    val arrEndReg = (arrStartReg + arr.registerCount)

    ActionEnumsFingerprint.method.apply {
        val lastNewInstance = instructions.last { it.opcode == Opcode.NEW_INSTANCE }
        val lastNewInstanceReg = lastNewInstance.registersUsed[0]
        val stringReg = lastNewInstanceReg + 1
        val constReg = stringReg + 1

        val lastInvokeStaticIndex = instructions.last { it.opcode == Opcode.INVOKE_STATIC }.location.index
        addInstructions(
            lastInvokeStaticIndex,
            """
            new-instance v$lastNewInstanceReg, $classDef
            const-string v$stringReg, "$name"
            const/16 v$constReg, ${arrEndReg - arrStartReg}
            invoke-direct {v$lastNewInstanceReg, v$stringReg, v$constReg}, $classDef-><init>(Ljava/lang/String;I)V
            sput-object v$lastNewInstanceReg, $fieldCall
            """.trimIndent(),
        )
    }

    oldValuesInstructions[newArrayPos] =
        """
        filled-new-array/range {v$arrStartReg .. v${arrEndReg + arrStartReg}}, [$classDef
        """.trimIndent().toInstruction()

    oldValuesInstructions.add(
        newArrayPos,
        """
        sget-object v$arrEndReg, $fieldCall
        """.trimIndent().toInstruction(),
    )

    val instructions =
        oldValuesInstructions.map { ins ->
            instructionToString(ins)
        }

    val newInitImplementation = MethodImplementationBuilder(arrEndReg + 1)
    instructions.forEach { ins ->
        newInitImplementation.addInstruction(ins.toInstruction())
    }

    val newInit =
        InitMethod(
            validator = { oldValues.validateReference() },
            compare = { oldValues.compareTo(it) },
            definingClass = oldValues.definingClass,
            name = oldValues.name,
            parameterTypes = oldValues.parameterTypes,
            returnType = oldValues.returnType,
            annotations = oldValues.annotations,
            accessFlags = oldValues.accessFlags,
            hiddenApiRestrictions = oldValues.hiddenApiRestrictions,
            parameters = oldValues.parameters,
            implementation = newInitImplementation.methodImplementation,
        )

    classMethods.removeIf { it.name == methodName }
    classMethods.add(MutableMethod(newInit))
    return fieldCall
}

context(patchContext: BytecodePatchContext)
fun addAction(name: String): String {
    return if (is_11_92_or_greater) {
        addActionV2(name)
    } else {
        addActionV1(name)
    }
}
