package app.crimera.utils

import app.crimera.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.utils.Constants.SSTS_DESCRIPTOR
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.util.*
import app.revanced.util.inputStreamFromBundledResource
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import org.w3c.dom.Element

fun ResourcePatchContext.mergeXmlResources(
    sourceResourceDirectory: String,
    vararg resourceGroups: ResourceGroup,
    tagName: String = "resources",
) {
    for (resourceGroup in resourceGroups) {
        resourceGroup.resources.forEach { resource ->
            val resourceFile = "${resourceGroup.resourceDirectoryName}/$resource"
            val targetFile = get("res").resolve(resourceFile)

            // If target file doesn't exist, use copyResources as fallback
            if (!targetFile.exists()) {
                copyResources(sourceResourceDirectory, resourceGroup)
                return@forEach
            }

            inputStreamFromBundledResource(sourceResourceDirectory, resourceFile)?.let { inputStream ->
                tagName
                    .copyXmlNode(
                        document(inputStream),
                        document("res/$resourceFile"),
                    ).close()
            } ?: throw PatchException("Could not find $resourceFile in $sourceResourceDirectory")
        }
    }
}

fun ResourcePatchContext.replaceXmlResources(
    sourceResourceDirectory: String,
    vararg resourceGroups: ResourceGroup,
) {
    resourceGroups.forEach { resourceGroup ->
        resourceGroup.resources.forEach { resource ->
            val sourceFile = "${resourceGroup.resourceDirectoryName}/$resource"
            val targetFile = "res/$sourceFile"
            println(targetFile)

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

fun MutableMethod.enableSettings(functionName: String) {
    addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

fun MutableMethod.flagSettings(functionName: String) {
    addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}

fun Reference.extractDescriptors(): List<String> {
    val regex = Regex("L[^;]+;")
    return regex.findAll(this.toString()).map { it.value }.toList()
}

fun MutableMethod.getReference(index: Int): Reference = getInstruction<ReferenceInstruction>(index).reference

fun MutableMethod.getMethodName(index: Int): String = getInstruction<DexBackedMethodReference>(index).name

fun MutableMethod.getFieldName(index: Int): String = getInstruction<FieldReference>(index).name

fun MutableMethod.changeStringAt(
    index: Int,
    value: String,
) {
    instructions.filter { it.opcode == Opcode.CONST_STRING }[index].let { instruction ->
        val register = (instruction as BuilderInstruction21c).registerA
        replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
    }
}

fun MutableMethod.changeFirstString(value: String) {
    changeStringAt(0, value)
}
