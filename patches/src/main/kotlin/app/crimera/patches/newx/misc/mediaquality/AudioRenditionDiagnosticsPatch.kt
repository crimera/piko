/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.mediaquality

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.utils.Constants.AUDIO_RENDITION_DIAGNOSTICS_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Diagnostic only: the audio-rendition pattern is the anchor, and the holder is resolved from it at patch time.
private const val AUDIO_RENDITION_PATTERN = "audio-(\\d+)"
private const val REGEX_DESCRIPTOR = "Lkotlin/text/Regex;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LOG_PLAYLIST_METHOD = "$AUDIO_RENDITION_DIAGNOSTICS_DESCRIPTOR->logPlaylist(Ljava/lang/Object;)V"
private const val LOG_RENDITION_METHOD =
    "$AUDIO_RENDITION_DIAGNOSTICS_DESCRIPTOR->logRendition($STRING_DESCRIPTOR" + "Ljava/lang/Object;)V"
private const val LOG_REQUEST_METHOD = "$AUDIO_RENDITION_DIAGNOSTICS_DESCRIPTOR->logRequest(Ljava/lang/Object;)V"

/** Resolve DataSource.open through the parser's InputStream adapter, not obfuscated names. */
context(context: BytecodePatchContext)
private fun patchStreamRequests(parser: Match) {
    val dataSpec = requireExactlyOne(
        "NewX diagnostic DataSpec",
        context.classDefByStrings("DataSpec["),
    )
    val stream = context.classDefBy(parser.originalMethod.parameterTypes[1].toString())
    if (stream.superclass != "Ljava/io/InputStream;") {
        throw PatchException("NewX diagnostic parser input is not an InputStream: ${stream.type}")
    }
    val constructor = requireExactlyOne(
        "NewX diagnostic DataSourceInputStream constructor",
        stream.methods.filter { method ->
            method.name == "<init>" && method.parameterTypes.size == 2 &&
                method.parameterTypes[1].toString() == dataSpec.type
        },
    )
    val source = context.classDefBy(constructor.parameterTypes[0].toString())
    val open = requireExactlyOne(
        "NewX diagnostic DataSource open contract",
        source.methods.filter { method ->
            method.returnType == "J" && method.parameterTypes.map { it.toString() } == listOf(dataSpec.type)
        },
    )
    // Many implementations/wrappers are intentional: include cache hits as well as network loads.
    val matches = Fingerprint(
        name = open.name,
        returnType = "J",
        parameters = listOf(dataSpec.type),
    ).matchAllOrNull().orEmpty().filter { it.originalMethod.implementation != null }
    if (matches.isEmpty()) {
        throw PatchException("NewX diagnostic: no concrete DataSource open implementations for $open")
    }
    matches.forEach { match ->
        val method = match.method
        method.insertHook(index = 0, relocateBranchTargets = true) {
            invokeStatic(methodReference(LOG_REQUEST_METHOD), parameterRegister(method, 0))
        }
    }
}

/**
 * The regex holder is the static field that a class stores in its `<clinit>` after building the audio pattern.
 * Resolving it from the string keeps the patch independent of the obfuscated owner name.
 */
context(context: BytecodePatchContext)
private fun audioRegexField(): FieldReference {
    val fields =
        context.classDefByStrings(AUDIO_RENDITION_PATTERN)
            .flatMap { classDef ->
                classDef.methods
                    .filter { method -> method.name == "<clinit>" }
                    .flatMap { method ->
                        method.implementation?.instructions?.toList().orEmpty()
                            .filter { instruction ->
                                instruction.opcode == Opcode.SPUT_OBJECT &&
                                    instruction.getReference<FieldReference>()?.type == REGEX_DESCRIPTOR
                            }
                            .mapNotNull { instruction -> instruction.getReference<FieldReference>() }
                    }
            }
            .distinctBy { field -> field.toString() }
    if (fields.isEmpty()) {
        throw PatchException(
            "NewX audio rendition diagnostic: no static Regex for \"$AUDIO_RENDITION_PATTERN\" is stored in a " +
                "<clinit>. This diagnostic targets the 12.34+ HLS playlist parser.",
        )
    }
    return requireExactlyOne("NewX audio-rendition Regex holder", fields) { field -> field.toString() }
}

/** The one method that reads the holder's Regex and calls `matchEntire` on it. */
context(context: BytecodePatchContext)
private fun audioRenditionMatchSite(field: FieldReference): Match {
    val matches =
        Fingerprint(
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.SGET_OBJECT,
                    definingClass = field.definingClass,
                    name = field.name,
                    type = REGEX_DESCRIPTOR,
                ),
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    definingClass = REGEX_DESCRIPTOR,
                    name = "matchEntire",
                ),
            ),
        ).matchAllOrNull().orEmpty()
    return requireExactlyOne("NewX audio-rendition matchEntire site", matches) { match ->
        match.originalMethod.toString()
    }
}

private fun registerWidth(type: CharSequence): Int =
    if (type.toString() == "J" || type.toString() == "D") 2 else 1

/** Register of the parameter at [index] (receiver excluded), as in ProfilePhotosGalleryPatch. */
private fun parameterRegister(method: Method, index: Int): Int {
    val implementation = method.implementation
        ?: throw PatchException("NewX audio rendition method has no implementation: $method")
    val parameterWidth = method.parameterTypes.sumOf(::registerWidth)
    val receiverWidth = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    var register = implementation.registerCount - parameterWidth - receiverWidth + receiverWidth
    repeat(index) { parameterIndex ->
        register += registerWidth(method.parameterTypes[parameterIndex])
    }
    return register
}

context(context: BytecodePatchContext)
private fun patchRenditionDiagnostics(match: Match) {
    val method = match.method
    val instructions = method.implementation?.instructions?.toList()
        ?: throw PatchException("NewX audio rendition method has no implementation: $method")
    val matchEntireIndex = match.instructionMatches[1].index
    val invoke = instructions[matchEntireIndex]
    if (invoke.opcode != Opcode.INVOKE_VIRTUAL) {
        throw PatchException("NewX audio rendition matchEntire is not an invoke-virtual at $matchEntireIndex in $method")
    }
    val operands = invoke.registersUsed
    if (operands.size != 2) {
        throw PatchException("NewX audio rendition matchEntire expects a receiver and one id, found $operands in $method")
    }
    val idRegister = operands[1]

    val resultInstruction = instructions.getOrNull(matchEntireIndex + 1)
    if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX audio rendition matchEntire is not followed by move-result-object in $method")
    }
    val resultRegister = (resultInstruction as OneRegisterInstruction).registerA
    // Fail closed: if the result overwrote the id, the id could not be logged.
    if (resultRegister == idRegister) {
        throw PatchException(
            "NewX audio rendition match result reuses the id register v$idRegister in $method; the id cannot be logged",
        )
    }

    // Insert the later hook first, so the earlier index stays valid.
    method.insertHook(index = matchEntireIndex + 2, relocateBranchTargets = true) {
        invokeStatic(methodReference(LOG_RENDITION_METHOD), idRegister, resultRegister)
    }
    method.insertHook(index = 0, relocateBranchTargets = true) {
        invokeStatic(methodReference(LOG_PLAYLIST_METHOD), parameterRegister(method, 0))
    }
}

@Suppress("unused")
val newXAudioRenditionDiagnosticsPatch =
    bytecodePatch(
        name = "NewX: Log HLS audio renditions",
        description = "Diagnostic only. Logs HLS playlist URIs and how rendition ids match the audio pattern. " +
            "Needs piko logging enabled.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val parser = audioRenditionMatchSite(audioRegexField())
            patchStreamRequests(parser)
            patchRenditionDiagnostics(parser)
        }
    }
