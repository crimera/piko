package app.crimera.patches.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import java.util.WeakHashMap

internal data class MethodShape(
    val returnType: String,
    val parameterCount: Int,
)

private data class IndexedMethod(
    val originalClass: ClassDef,
    val originalMethod: Method,
) {
    fun resolveCurrent(context: BytecodePatchContext): Method? {
        val currentClass = context.classDefByOrNull(originalClass.type) ?: return null
        if (currentClass === originalClass) return originalMethod

        return currentClass.methods.singleOrNull { currentMethod ->
            currentMethod.name == originalMethod.name &&
                currentMethod.returnType == originalMethod.returnType &&
                currentMethod.parameterTypes == originalMethod.parameterTypes
        }
    }
}

private object FingerprintCandidateCache {
    private val allClassDescriptors = WeakHashMap<BytecodePatchContext, List<String>>()
    private val scopedClassDescriptors =
        WeakHashMap<BytecodePatchContext, MutableMap<String, List<String>>>()
    private val methodsByShape =
        WeakHashMap<BytecodePatchContext, Map<MethodShape, List<IndexedMethod>>>()

    fun classDescriptors(
        context: BytecodePatchContext,
        scope: String,
        inScope: (String) -> Boolean,
    ): List<String> =
        synchronized(scopedClassDescriptors) {
            val allDescriptors =
                allClassDescriptors.getOrPut(context) {
                    buildList {
                        context.classDefForEach { classDef -> add(classDef.type) }
                    }
                }
            scopedClassDescriptors
                .getOrPut(context, ::mutableMapOf)
                .getOrPut(scope) { allDescriptors.filter(inScope) }
        }

    fun methods(
        context: BytecodePatchContext,
        shape: MethodShape,
    ): List<Method> =
        synchronized(methodsByShape) {
            methodsByShape.getOrPut(context) {
                buildMap<MethodShape, MutableList<IndexedMethod>> {
                    context.classDefForEach { classDef ->
                        classDef.methods.forEach { method ->
                            val methodShape = MethodShape(method.returnType, method.parameterTypes.size)
                            getOrPut(methodShape, ::mutableListOf).add(IndexedMethod(classDef, method))
                        }
                    }
                }
            }[shape].orEmpty().mapNotNull { method -> method.resolveCurrent(context) }
        }
}

private fun String.isExactTypeDeclaration(): Boolean =
    length == 1 && single() in "BCDFIJSVZ" ||
        startsWith('L') && endsWith(';') ||
        startsWith('[') && endsWith(';')

/**
 * Fingerprint without an owner scope that records its exact method shape at declaration time.
 *
 * Morphe made `Fingerprint.returnType` and `Fingerprint.parameters` internal, so the scoped matcher
 * cannot read the declaration from the [Fingerprint] instance anymore. Shape-only fingerprints must
 * extend this class to keep using the shape index; plain [Fingerprint]s without an owner scope are
 * still matched, but they fall back to the global matcher.
 */
internal open class ShapeFingerprint(
    returnType: String? = null,
    parameters: List<String>? = null,
    name: String? = null,
    accessFlags: List<AccessFlags>? = null,
    filters: List<InstructionFilter>? = null,
    strings: List<String>? = null,
    custom: ((method: Method, classDef: ClassDef) -> Boolean)? = null,
) : Fingerprint(
    name = name,
    accessFlags = accessFlags,
    returnType = returnType,
    parameters = parameters,
    filters = filters,
    strings = strings,
    custom = custom,
) {
    internal val declaredShape: MethodShape? =
        if (returnType == null || parameters == null) {
            null
        } else {
            returnType.takeIf(String::isExactTypeDeclaration)?.let { MethodShape(it, parameters.size) }
        }
}

/**
 * Matches every method while pre-scoping exact owners, preserved owner prefixes, and exact method
 * shapes. Unlike Morphe's global all-match path, owner scopes are resolved before method matching.
 */
context(context: BytecodePatchContext)
internal fun Fingerprint.scopedMatchAllOrNull(): List<Match>? {
    val nestedClassFingerprint = classFingerprint
    if (nestedClassFingerprint != null) {
        val originalClass = nestedClassFingerprint.matchOrNull()?.originalClassDef ?: return null
        val classDef = context.classDefByOrNull(originalClass.type) ?: return null
        val matches = buildList {
            classDef.methods.forEach { method ->
                val match = matchOrNull(method, classDef) ?: return@forEach
                add(match)
                clearMatch()
            }
        }
        return matches.ifEmpty { null }
    }

    // Declared owner scope. Morphe hides the field but keeps a `getDefiningClass()` binary
    // compatibility shim for bundles compiled against earlier patcher versions.
    val classScope = definingClass
    if (classScope == null) {
        // `returnType`/`parameters` are internal in current Morphe patchers, so the shape comes from
        // the piko-owned declaration instead of a hidden patcher field.
        val shape = (this as? ShapeFingerprint)?.declaredShape ?: return matchAllOrNull()

        val candidates = FingerprintCandidateCache.methods(context, shape)
        val matches = buildList {
            candidates.forEach { method ->
                val classDef = context.classDefByOrNull(method.definingClass) ?: return@forEach
                val match = matchOrNull(method, classDef) ?: return@forEach
                add(match)
                clearMatch()
            }
        }
        return matches.ifEmpty { null }
    }

    val exactClass = classScope.startsWith('L') && classScope.endsWith(';')
    if (exactClass) {
        val classDef = context.classDefByOrNull(classScope) ?: return null
        return matchAllOrNull(classDef)
    }

    fun inScope(descriptor: String) = when {
        classScope.startsWith('L') || classScope.startsWith('[') -> descriptor.startsWith(classScope)
        classScope.endsWith(';') -> descriptor.endsWith(classScope)
        else -> descriptor.contains(classScope)
    }

    val classDescriptors =
        FingerprintCandidateCache.classDescriptors(context, classScope, ::inScope)
    val matches = buildList {
        classDescriptors.forEach { descriptor ->
            val classDef = context.classDefByOrNull(descriptor) ?: return@forEach
            addAll(matchAllOrNull(classDef).orEmpty())
        }
    }
    return matches.ifEmpty { null }
}

context(_: BytecodePatchContext)
internal fun Fingerprint.scopedMatchAll(): List<Match> =
    scopedMatchAllOrNull() ?: throw patchException()
