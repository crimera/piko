package app.revanced.util

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableClass
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.util.MethodUtil

fun MethodFingerprint.resultOrThrow() = result ?: throw exception

/**
 * The [PatchException] of failing to resolve a [MethodFingerprint].
 *
 * @return The [PatchException].
 */
val MethodFingerprint.exception
    get() = PatchException("Failed to resolve ${this.javaClass.simpleName}")

/**
 * Find the [MutableMethod] from a given [Method] in a [MutableClass].
 *
 * @param method The [Method] to find.
 * @return The [MutableMethod].
 */
fun MutableClass.findMutableMethodOf(method: Method) = this.methods.first {
    MethodUtil.methodSignaturesMatch(it, method)
}

/**
 * Apply a transform to all methods of the class.
 *
 * @param transform The transformation function. Accepts a [MutableMethod] and returns a transformed [MutableMethod].
 */
fun MutableClass.transformMethods(transform: MutableMethod.() -> MutableMethod) {
    val transformedMethods = methods.map { it.transform() }
    methods.clear()
    methods.addAll(transformedMethods)
}

/**
 * Inject a call to a method that hides a view.
 *
 * @param insertIndex The index to insert the call at.
 * @param viewRegister The register of the view to hide.
 * @param classDescriptor The descriptor of the class that contains the method.
 * @param targetMethod The name of the method to call.
 */
fun MutableMethod.injectHideViewCall(
    insertIndex: Int,
    viewRegister: Int,
    classDescriptor: String,
    targetMethod: String,
) = addInstruction(
    insertIndex,
    "invoke-static { v$viewRegister }, $classDescriptor->$targetMethod(Landroid/view/View;)V",
)

/**
 * Find the index of the first wide literal instruction with the given value.
 *
 * @return the first literal instruction with the value, or -1 if not found.
 */
fun Method.indexOfFirstWideLiteralInstructionValue(literal: Long) = implementation?.let {
    it.instructions.indexOfFirst { instruction ->
        (instruction as? WideLiteralInstruction)?.wideLiteral == literal
    }
} ?: -1

/**
 * Check if the method contains a literal with the given value.
 *
 * @return if the method contains a literal with the given value.
 */
fun Method.containsWideLiteralInstructionValue(literal: Long) =
    indexOfFirstWideLiteralInstructionValue(literal) >= 0

/**
 * Traverse the class hierarchy starting from the given root class.
 *
 * @param targetClass the class to start traversing the class hierarchy from.
 * @param callback function that is called for every class in the hierarchy.
 */
fun BytecodeContext.traverseClassHierarchy(targetClass: MutableClass, callback: MutableClass.() -> Unit) {
    callback(targetClass)
    this.findClass(targetClass.superclass ?: return)?.mutableClass?.let {
        traverseClassHierarchy(it, callback)
    }
}

/**
 * Get the [Reference] of an [Instruction] as [T].
 *
 * @param T The type of [Reference] to cast to.
 * @return The [Reference] as [T] or null
 * if the [Instruction] is not a [ReferenceInstruction] or the [Reference] is not of type [T].
 * @see ReferenceInstruction
 */
inline fun <reified T : Reference> Instruction.getReference() = (this as? ReferenceInstruction)?.reference as? T

/**
 * Get the index of the first [Instruction] that matches the predicate.
 *
 * @param predicate The predicate to match.
 * @return The index of the first [Instruction] that matches the predicate.
 */
fun Method.indexOfFirstInstruction(predicate: Instruction.() -> Boolean) =
    this.implementation!!.instructions.indexOfFirst(predicate)

/**
 * Return the resolved methods of [MethodFingerprint]s early.
 */
fun List<MethodFingerprint>.returnEarly(bool: Boolean = false) {
    val const = if (bool) "0x1" else "0x0"
    this.forEach { fingerprint ->
        fingerprint.result?.let { result ->
            val stringInstructions = when (result.method.returnType.first()) {
                'L' ->
                    """
                        const/4 v0, $const
                        return-object v0
                        """
                'V' -> "return-void"
                'I', 'Z' ->
                    """
                        const/4 v0, $const
                        return v0
                        """
                else -> throw Exception("This case should never happen.")
            }

            result.mutableMethod.addInstructions(0, stringInstructions)
        } ?: throw fingerprint.exception
    }
}