package app.crimera.utils

import app.crimera.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.utils.Constants.SSTS_DESCRIPTOR
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.util.*
import app.revanced.util.inputStreamFromBundledResource
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.*
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import org.w3c.dom.Element

fun ResourcePatchContext.replaceXmlResources(
    sourceResourceDirectory: String,
    vararg resourceGroups: ResourceGroup,
) {
    resourceGroups.forEach { resourceGroup ->
        resourceGroup.resources.forEach { resource ->
            val sourceFile = "${resourceGroup.resourceDirectoryName}/$resource"
            val targetFile = "res/$sourceFile"

            // If target file doesn't exist, use copyResources as fallback
            if (!get("res").resolve(sourceFile).exists()) {
                copyResources(sourceResourceDirectory, resourceGroup)
                return@forEach
            }

            // Load replacement values from the source resource
            val replacementDoc = document(inputStreamFromBundledResource(sourceResourceDirectory, sourceFile)!!)

            // Apply replacements to the target resource
            document(targetFile).use { targetDoc ->
                val replacementNodes = replacementDoc.getElementsByTagName("string")
                val targetNodes = targetDoc.getElementsByTagName("string")

                // Single loop: iterate through replacement strings and apply them directly
                for (i in 0 until replacementNodes.length) {
                    val replacementNode = replacementNodes.item(i) as Element
                    val stringName = replacementNode.getAttribute("name")
                    val replacementValue = replacementNode.textContent

                    // Find and replace the corresponding element in target document
                    val targetElement = targetNodes.findElementByAttributeValue("name", stringName)
                    targetElement?.textContent = replacementValue
                }
            }

            replacementDoc.close()
        }
    }
}

fun ResourcePatchContext.replaceStringsInFile(
    filePath: String,
    replacements: Map<String, String>,
) {
    val file = get(filePath)
    var content = file.readText()

    replacements.forEach { (from, to) ->
        content = content.replace(from, to)
    }

    file.writeText(content)
}

context(BytecodePatchContext)
fun Fingerprint.enableSettings(functionName: String) {
    method.addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

context(BytecodePatchContext)
fun Fingerprint.flagSettings(functionName: String) {
    method.addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}

fun Reference.extractDescriptors(): List<String> {
    val regex = Regex("L[^;]+;")
    return regex.findAll(this.toString()).map { it.value }.toList()
}

context(BytecodePatchContext)
fun Fingerprint.getReference(index: Int): Reference =
    method.getInstruction<ReferenceInstruction>(index).reference

context(BytecodePatchContext)
fun Fingerprint.getMethodName(index: Int): String = (getReference(index) as DexBackedMethodReference).name

context(BytecodePatchContext)
fun Fingerprint.getFieldName(index: Int): String = (getReference(index) as FieldReference).name

context(BytecodePatchContext)
fun Fingerprint.changeStringAt(
    index: Int,
    value: String,
) {
    method.instructions.filter { it.opcode == Opcode.CONST_STRING }[index].let { instruction ->
        val register = (instruction as BuilderInstruction21c).registerA
        method.replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
    }
}

context(BytecodePatchContext)
fun Fingerprint.changeFirstString(value: String) {
    changeStringAt(0, value)
}

fun instructionToString(ins: Instruction): String =
    when (ins) {
        is Instruction21c -> {
            if (ins.opcode == Opcode.CONST_STRING) {
                "${ins.opcode.name} v${ins.registerA}, \"${ins.reference}\""
            } else {
                "${ins.opcode.name} v${ins.registerA}, ${ins.reference}"
            }
        }

        is Instruction11n -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        is Instruction35c -> {
            var regs = ""

            val regMap =
                mapOf(
                    1 to ins.registerC,
                    2 to ins.registerD,
                    3 to ins.registerE,
                    4 to ins.registerF,
                    5 to ins.registerG,
                )

            for (i in 1..ins.registerCount) {
                regs += "v${regMap[i]}"

                if (ins.registerCount != i) {
                    regs += ", "
                }
            }
            "${ins.opcode.name} {$regs}, ${ins.reference}"
        }

        is Instruction21s -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        is Instruction22x -> {
            "${ins.opcode.name} v${ins.registerA}, v${ins.registerB}"
        }

        is Instruction3rc -> {
            "${ins.opcode.name} {v${ins.startRegister} .. v${ins.registerCount - 1}}, ${ins.reference}"
        }

        is Instruction11x -> {
            "${ins.opcode.name} v${ins.registerA}"
        }

        is Instruction10x -> {
            ins.opcode.name
        }

        is Instruction31i -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        else -> {
            throw PatchException("${ins.javaClass} is not supported")
        }
    }

val MutableList<BuilderInstruction>.indexOfLastNewInstance
    get() = this.indexOfLast { it.opcode == Opcode.NEW_INSTANCE }

val MutableList<BuilderInstruction>.indexOfLastFilledNewArrayRange
    get() = this.indexOfLast { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }

class InitMethod(
    private val validator: () -> Unit,
    private val compare: (other: MethodReference) -> Int,
    private val definingClass: String,
    private val name: String,
    private val parameterTypes: MutableList<out CharSequence>,
    private val returnType: String,
    private val annotations: MutableSet<out Annotation>,
    private val accessFlags: Int,
    private val hiddenApiRestrictions: MutableSet<HiddenApiRestriction>,
    private val parameters: MutableList<out MethodParameter>,
    private val implementation: MethodImplementation,
) : Method {
    override fun validateReference() = validator()

    override fun compareTo(other: MethodReference): Int = this.compare(other)

    override fun getDefiningClass(): String = definingClass

    override fun getName(): String = name

    override fun getParameterTypes(): MutableList<out CharSequence> = parameterTypes

    override fun getReturnType(): String = returnType

    override fun getAnnotations(): MutableSet<out Annotation> = annotations

    override fun getAccessFlags(): Int = accessFlags

    override fun getHiddenApiRestrictions(): MutableSet<HiddenApiRestriction> = hiddenApiRestrictions

    override fun getParameters(): MutableList<out MethodParameter> = parameters

    override fun getImplementation(): MethodImplementation = implementation
}
