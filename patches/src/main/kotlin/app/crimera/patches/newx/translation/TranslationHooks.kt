package app.crimera.patches.newx.translation

import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.models.patchBridge
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"

/**
 * Replaces the completed-state bridge body using only its seven local registers. Its original
 * callback, translated text, translated poll list, and source-language parameters remain intact.
 */
internal fun MutableMethod.injectCompletedBridge(
    entityListConstructor: MethodReference,
    translatedPostConstructor: MethodReference,
    completedConstructor: MethodReference,
    emptyListGetter: MethodReference,
) {
    if (!AccessFlags.STATIC.isSet(accessFlags)) {
        throw PatchException("Translation completed bridge must be static: $this")
    }
    val expectedParameters =
        listOf(OBJECT_DESCRIPTOR, STRING_DESCRIPTOR, LIST_DESCRIPTOR, STRING_DESCRIPTOR)
    if (parameterTypes.map(CharSequence::toString) != expectedParameters || returnType != "V") {
        throw PatchException("Unexpected translation completed bridge signature: $this")
    }

    val callback = p0Register
    val translatedText = callback + 1
    val translatedPolls = callback + 2
    val sourceLanguage = callback + 3
    if (numberOfParameterRegisters != expectedParameters.size || callback < 7 || callback + 3 > 15) {
        throw PatchException(
            "Translation completed bridge needs seven locals and four low parameter registers: $this",
        )
    }

    requireConstructor(
        entityListConstructor,
        List(6) { LIST_DESCRIPTOR },
        "translated-post entity list",
    )
    requireConstructor(
        translatedPostConstructor,
        listOf(STRING_DESCRIPTOR, entityListConstructor.definingClass, LIST_DESCRIPTOR),
        "translated post",
    )
    requireConstructor(
        completedConstructor,
        listOf(OBJECT_DESCRIPTOR, STRING_DESCRIPTOR),
        "completed translation state",
    )
    if (emptyListGetter.parameterTypes.isNotEmpty() || emptyListGetter.returnType != LIST_DESCRIPTOR) {
        throw PatchException("Unexpected empty-list factory reference: $emptyListGetter")
    }

    patchBridge {
        val entity = 0
        val emptyList = 1
        val content = 1
        val completed = 2

        newInstance(entity, entityListConstructor.definingClass)
        invokeStatic(emptyListGetter)
        moveResult(emptyList, LIST_DESCRIPTOR)
        // The six-list constructor needs a contiguous v0..v6 range.
        (2..6).forEach { register -> move(register, emptyList, LIST_DESCRIPTOR) }
        invokeDirect(entityListConstructor, entity, emptyList, 2, 3, 4, 5, 6)

        newInstance(content, translatedPostConstructor.definingClass)
        invokeDirect(translatedPostConstructor, content, translatedText, entity, translatedPolls)

        newInstance(completed, completedConstructor.definingClass)
        invokeDirect(completedConstructor, completed, content, sourceLanguage)
        checkCast(callback, FUNCTION1_DESCRIPTOR)
        invokeInterface(
            methodReference("$FUNCTION1_DESCRIPTOR->invoke($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"),
            callback,
            completed,
        )
        returnVoid()
    }
}

private fun requireConstructor(
    reference: MethodReference,
    expectedParameters: List<String>,
    label: String,
) {
    if (reference.name != "<init>" || reference.returnType != "V" ||
        reference.parameterTypes.map(CharSequence::toString) != expectedParameters
    ) {
        throw PatchException("Unexpected $label constructor reference: $reference")
    }
}
