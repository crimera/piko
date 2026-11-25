/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.roundedcorners

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference

private const val PATCH = "Rounded corners"
private const val ANY = "Ljava/lang/Object;"

private val BUFFER_SHAPE = listOf(
    Opcode.IPUT, Opcode.MUL_INT_LIT8, Opcode.CONST_4, Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.ADD_INT_LIT8,
    Opcode.NEW_ARRAY, Opcode.IPUT_OBJECT, Opcode.NEW_ARRAY, Opcode.IPUT_OBJECT,
)

@Suppress("unused")
val roundedCornersPatch =
    bytecodePatch(
        name = "Rounded media corners in feed and story",
        description = "Rounds the corners of feed post media and stories, and optionally shows the next carousel " +
            "page peeking in from the right edge.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            val site = findRoundedSite()
            val buffer = findPagerBuffer()
            applyRoundedCorners(site)
            applyPagerBuffer(buffer)
            enableSettings("roundedStoryCorners")
            enableSettings("roundedPostCorners")
            enableSettings("carouselPeek")
        }
    }

private fun refuse(detail: String): Nothing = throw PatchException("$PATCH: $detail")

private fun ClassDef.instanceFields() = fields.filter { !AccessFlags.STATIC.isSet(it.accessFlags) }

private fun ClassDef.constructsGestureDetector(): Boolean {
    return methods.any { method ->
        val implementation = method.implementation ?: return@any false
        implementation.instructions.any { instruction ->
            instruction.opcode.name.startsWith("invoke") &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let { it.definingClass == GESTURE_DETECTOR && it.name == "<init>" } == true
        }
    }
}

internal class RoundedSite(val make: Method, val frame: FieldReference)

private fun BytecodePatchContext.findRoundedSite(): RoundedSite {
    val candidates = mutableListOf<ClassDef>()
    classDefForEach { classDef ->
        val types = classDef.instanceFields().map { it.type }
        if (MEDIA_FRAME in types && MEDIA in types) candidates += classDef
    }
    val holders = candidates.filter { it.constructsGestureDetector() }
    val holder = holders.singleOrNull()
        ?: refuse("expected one class making a GestureDetector that keeps a media frame and a Media, found ${holders.size}")
    if (!AccessFlags.PUBLIC.isSet(holder.accessFlags)) refuse("${holder.type} isn't public, so the extension can't read it")

    val frames = holder.instanceFields().filter { it.type == MEDIA_FRAME }
    val frame = frames.singleOrNull()
        ?: refuse("expected ${holder.type} to keep the media frame ($MEDIA_FRAME) in one field, found ${frames.size}")
    if (!AccessFlags.PUBLIC.isSet(frame.accessFlags)) refuse("${holder.type}->${frame.name}, the media frame, isn't public, so the extension can't read it")

    val makes = holder.methods.filter { it.name == "<init>" && it.implementation != null }
    val make = makes.singleOrNull() ?: refuse("expected ${holder.type} to have one constructor, found ${makes.size}")
    val code = make.implementation!!.instructions.toList()
    val returns = code.indices.filter { code[it].opcode == Opcode.RETURN_VOID }
    if (returns.isEmpty()) refuse("${holder.type}'s constructor never returns void")
    if (returns.any { it in make.jumpTargets() }) refuse("something in ${holder.type}'s constructor jumps to a return")
    val self = make.implementation!!.registerCount - 1 - make.parameterWords()
    if (code.any { it.writes(self) }) refuse("something in ${holder.type}'s constructor writes the register holding this")
    return RoundedSite(make, ImmutableFieldReference(frame.definingClass, frame.name, frame.type))
}

private fun BytecodePatchContext.applyRoundedCorners(site: RoundedSite) {
    val make: MutableMethod = mutableClassDefBy(site.make.definingClass).methods.single {
        it.name == "<init>" && it.parameterTypes.map(Any::toString) == site.make.parameterTypes.map(Any::toString)
    }
    val code = make.implementation!!.instructions.toList()
    for (at in code.indices.reversed()) {
        if (code[at].opcode == Opcode.RETURN_VOID) make.addInstructions(at, "invoke-static/range { p0 .. p0 }, $ROUNDED_MADE")
    }
    val stub = mutableClassDefBy(ROUNDED_TARGETS).methods.singleOrNull {
        it.name == "frame" && AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == ANY &&
            it.parameterTypes.map(Any::toString) == listOf(ANY)
    } ?: refuse("$ROUNDED_TARGETS has no static Object frame(Object)")
    stub.addInstructions(
        0,
        """
            check-cast p0, ${site.make.definingClass}
            iget-object p0, p0, ${site.frame}
            return-object p0
        """.trimIndent(),
    )
}

internal class BufferSite(val set: Method, val pages: Int)

private fun Method.parameterWords() = parameterTypes.sumOf { if (it.toString() == "J" || it.toString() == "D") 2 else 1 }

private fun BytecodePatchContext.findPagerBuffer(): BufferSite {
    val pager = classDefByOrNull(REBOUND_VIEW_PAGER) ?: refuse("no $REBOUND_VIEW_PAGER, Instagram's carousel pager")
    val sites = pager.methods.mapNotNull(::bufferSite)
    val site = sites.singleOrNull()
        ?: refuse("expected one method of $REBOUND_VIEW_PAGER keeping its buffer and sizing its page slots, found ${sites.size}")
    if (0 in site.set.jumpTargets()) refuse("something in $REBOUND_VIEW_PAGER->${site.set.name}, the buffer setter, jumps to its start")
    val self = site.set.implementation!!.registerCount - 1 - site.set.parameterWords()
    if (self + site.pages > 15) refuse("$REBOUND_VIEW_PAGER->${site.set.name}, the buffer setter, keeps the buffer past v15")
    return site
}

private fun bufferSite(method: Method): BufferSite? {
    if (AccessFlags.STATIC.isSet(method.accessFlags) || method.returnType != "V") return null
    val implementation = method.implementation ?: return null
    val code = implementation.instructions.take(BUFFER_SHAPE.size)
    if (code.map { it.opcode } != BUFFER_SHAPE) return null
    val self = implementation.registerCount - 1 - method.parameterWords()
    fun field(at: Int) = (code[at] as ReferenceInstruction).reference as FieldReference
    fun owner(at: Int) = (code[at] as TwoRegisterInstruction).registerB == self && field(at).definingClass == REBOUND_VIEW_PAGER

    val kept = code[0] as TwoRegisterInstruction
    if (!owner(0) || field(0).type != "I") return null
    val pages = kept.registerA - self
    var word = 1
    val parameter = method.parameterTypes.map(Any::toString).firstOrNull { type ->
        (word == pages).also { word += if (type == "J" || type == "D") 2 else 1 }
    }
    if (parameter != "I") return null

    val doubled = code[1] as TwoRegisterInstruction
    if (doubled.registerB != kept.registerA || (code[1] as NarrowLiteralInstruction).narrowLiteral != 2) return null
    if ((code[2] as NarrowLiteralInstruction).narrowLiteral != 1) return null
    if (((code[3] as ReferenceInstruction).reference).toString() != "Ljava/lang/Math;->max(II)I") return null
    if ((code[5] as NarrowLiteralInstruction).narrowLiteral != 1) return null
    val arrays = listOf(6, 8).map { ((code[it] as ReferenceInstruction).reference as TypeReference).type }
    if (arrays != listOf("[I", "[I")) return null
    if (!owner(7) || !owner(9) || field(7).type != "[I" || field(9).type != "[I" || field(7).name == field(9).name) return null
    return BufferSite(method, pages)
}

private fun BytecodePatchContext.applyPagerBuffer(site: BufferSite) {
    val set: MutableMethod = mutableClassDefBy(REBOUND_VIEW_PAGER).methods.single {
        it.name == site.set.name && it.parameterTypes.map(Any::toString) == site.set.parameterTypes.map(Any::toString)
    }
    set.addInstructions(
        0,
        """
            invoke-static { p0, p${site.pages} }, $PEEK_BUFFER
            move-result p${site.pages}
        """.trimIndent(),
    )
}

private fun Instruction.writes(register: Int): Boolean =
    this is OneRegisterInstruction && opcode.setsRegister() &&
        (registerA == register || (opcode.setsWideRegister() && registerA + 1 == register))

private fun Method.jumpTargets(): Set<Int> {
    val implementation = implementation ?: return emptySet()
    val code = implementation.instructions.toList()
    val offsets = IntArray(code.size)
    var at = 0
    for (i in code.indices) {
        offsets[i] = at
        at += code[i].codeUnits
    }
    val indexAt = HashMap<Int, Int>()
    for (i in code.indices) indexAt[offsets[i]] = i
    val targets = mutableSetOf<Int>()
    for (i in code.indices) {
        val instruction = code[i]
        if (instruction is OffsetInstruction) {
            indexAt[offsets[i] + instruction.codeOffset]?.let { targets += it }
        }
    }
    for (i in code.indices) {
        val instruction = code[i]
        if (instruction !is OffsetInstruction) continue
        if (instruction.opcode != Opcode.PACKED_SWITCH && instruction.opcode != Opcode.SPARSE_SWITCH) continue
        val payload = indexAt[offsets[i] + instruction.codeOffset]?.let { code[it] } as? SwitchPayload ?: continue
        for (element in payload.switchElements) {
            indexAt[offsets[i] + element.offset]?.let { targets += it }
        }
    }
    for (tryBlock in implementation.tryBlocks) {
        for (handler in tryBlock.exceptionHandlers) {
            indexAt[handler.handlerCodeAddress]?.let { targets += it }
        }
    }
    return targets
}
