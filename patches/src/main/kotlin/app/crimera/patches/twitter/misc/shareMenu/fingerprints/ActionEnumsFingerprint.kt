package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.crimera.utils.InitMethod
import app.crimera.utils.indexOfLastFilledNewArrayRange
import app.crimera.utils.indexOfLastNewInstance
import app.crimera.utils.instructionToString
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.smali.toInstruction
import app.revanced.patcher.util.smali.toInstructions
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc

val actionEnumsFingerprint =
    fingerprint {
        strings("None", "Favorite", "Retweet")
        custom { _, classDef ->
            classDef.type.contains("Lcom/twitter/model/core")
        }
    }

context(BytecodePatchContext)
fun addAction(name: String): String {
    val field =
        actionEnumsFingerprint.classDef.fields
            .last()
            .toMutable()
    field.name = name
    actionEnumsFingerprint.classDef.fields.add(field)

    // TODO: handle nulls
    val oldInit = actionEnumsFingerprint.classDef.methods.first { it.name == "<clinit>" }

    val oldInitInstructions = oldInit.implementation!!.instructions.toMutableList()

    val lastNewInstance = (oldInitInstructions[oldInitInstructions.indexOfLastNewInstance] as Instruction21c)
    val lastNewInstanceReg = lastNewInstance.registerA
    val stringReg = lastNewInstanceReg + 1
    val constReg = stringReg + 1

    val fieldCall = "${lastNewInstance.reference}->$name:${lastNewInstance.reference}"

    val newArrayPos = oldInitInstructions.indexOfLastFilledNewArrayRange

    var arr = oldInitInstructions.get(newArrayPos) as Instruction3rc
    var arrStartReg = arr.startRegister
    var arrEndReg = (arrStartReg + arr.registerCount)

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

    val actionsEnumClass = actionEnumsFingerprint.classDef
    actionsEnumClass.methods.removeIf { it.name == "<clinit>" }
    actionsEnumClass.methods.add(MutableMethod(newInit))
    return fieldCall
}
