package app.crimera.patches.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import java.util.WeakHashMap

private data class MethodShape(
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

    val classScope = definingClass
    if (classScope == null) {
        val exactReturnType = returnType?.takeIf(String::isExactTypeDeclaration)
        val parameterCount = parameters?.size
        if (exactReturnType == null || parameterCount == null) return matchAllOrNull()

        val candidates =
            FingerprintCandidateCache.methods(context, MethodShape(exactReturnType, parameterCount))
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
