/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.bringbacktwitter

import app.crimera.bytecode.insertHook
import app.crimera.patches.common.INTEGER_LITERAL_OPCODES
import app.crimera.patches.common.classDefFlatMap
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val XLOGO_PACKAGE = "Lcom/x/ui/common/xlogo/"
private const val REFRESH_PACKAGE = "Lcom/x/home/tabbed/refresh/"
private const val BIRD_ASSET = "/twitter/bringbacktwitter/drawable/ic_vector_twitter.xml"
private const val STROKE_COVERAGE_FACTOR = 0.7f
/** Tilt of the sweep line from vertical, toward the beak: the bird rises from bottom to top, banking slightly. */
private const val BIRD_RISE_TILT_DEGREES = 15.0
/** The arm factory extends both ends of every arm by this many units, so each arm is inset by the same amount. */
private const val BIRD_ARM_END_EXTENSION = 2

private val ARM_FACTORY_PARAMETERS = listOf("F", "J", "J")
private val PATH_DATA = Regex("""[Mm][MmLlHhVvCcSsQqTtAaZz0-9eE+.,\s-]*[Zz]""")
private val PATH_TOKEN = Regex("""[MmLlHhVvCcZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
private val PATH_DATA_ATTRIBUTE = Regex("""android:pathData="([^"]+)"""")

internal data class LogoMethodKey(
    val classType: String,
    val name: String,
    val parameterTypes: List<String>,
)

internal sealed interface LogoHit {
    data class XLogoClass(val type: String) : LogoHit

    data class RefreshClass(val type: String) : LogoHit

    data class Glyph(val owner: LogoMethodKey, val index: Int, val register: Int) : LogoHit

    /** An arm-factory call; [defIndex] is the thickness literal's write and [callIndex] the call itself. */
    data class Stroke(val owner: LogoMethodKey, val defIndex: Int, val callIndex: Int, val register: Int) : LogoHit

    /** A `Float.floatToRawIntBits(F)I` call: one arm coordinate is packed into a long here. */
    data class Coordinate(val owner: LogoMethodKey, val callIndex: Int, val register: Int) : LogoHit
}

internal class LogoSites(
    val glyph: LogoHit.Glyph,
    /** Arm-factory calls sorted by call index; arm k is `strokes[k]`. */
    val strokes: List<LogoHit.Stroke>,
    /** Per arm (same order as [strokes]): its four coordinate calls in ax, ay, bx, by order. */
    val coordinates: List<List<LogoHit.Coordinate>>,
)

/** One sweep arm: its stroke width and centre-line endpoints (a = tail side, b = beak side). */
internal data class SweepArm(val thickness: Float, val ax: Float, val ay: Float, val bx: Float, val by: Float)

internal fun isSvgPathData(value: String): Boolean = value.length >= 16 && PATH_DATA.matches(value)

/** Gate + cardinality. `null` means: an APK from before the procedural logo; nothing to patch. */
internal fun requireLogoSites(hits: List<LogoHit>): LogoSites? {
    val xlogoClasses = hits.filterIsInstance<LogoHit.XLogoClass>()
    val refreshClasses = hits.filterIsInstance<LogoHit.RefreshClass>()
    if (xlogoClasses.isEmpty()) {
        if (refreshClasses.isEmpty()) return null
        throw PatchException(
            "NewX top-bar logo: this APK has the pull-to-refresh package ($REFRESH_PACKAGE, ${refreshClasses.size} classes) " +
                "but no $XLOGO_PACKAGE classes. The animated logo moved; update the glyph resolver, otherwise the top bar keeps the X.",
        )
    }
    val glyph =
        requireExactlyOne(
            "NewX top-bar logo glyph path-data constant in $XLOGO_PACKAGE",
            hits.filterIsInstance<LogoHit.Glyph>(),
        )
    val strokes = hits.filterIsInstance<LogoHit.Stroke>().sortedBy { it.callIndex }
    if (strokes.isEmpty()) {
        throw PatchException(
            "NewX top-bar logo: $XLOGO_PACKAGE is present but no arm-factory call (static (F,J,J) returning an xlogo type) " +
                "with a float-literal thickness was found; the bird would be revealed only partially.",
        )
    }
    return LogoSites(glyph, strokes, armCoordinates(strokes, hits.filterIsInstance<LogoHit.Coordinate>()))
}

/**
 * Arm k's four coordinate calls (ax, ay, bx, by) sit between the previous arm-factory call and its own. Any other count
 * means the arm layout changed, so it fails instead of guessing which register holds which coordinate.
 */
private fun armCoordinates(
    strokes: List<LogoHit.Stroke>,
    coordinates: List<LogoHit.Coordinate>,
): List<List<LogoHit.Coordinate>> {
    val owners = strokes.map { it.owner }.distinct()
    if (owners.size != 1) {
        throw PatchException("NewX top-bar logo: the arm-factory calls span ${owners.size} methods; expected one sweep method")
    }
    val owner = owners.single()
    var previous = -1
    return strokes.mapIndexed { arm, stroke ->
        val group =
            coordinates
                .filter { it.owner == owner && it.callIndex > previous && it.callIndex < stroke.callIndex }
                .sortedBy { it.callIndex }
        previous = stroke.callIndex
        if (group.size != 4) {
            throw PatchException(
                "NewX top-bar logo: arm ${arm + 1} has ${group.size} float-to-bits coordinate calls, expected 4 (ax, ay, bx, by)",
            )
        }
        group
    }
}

internal fun birdPathData(xml: String): String {
    val data =
        requireExactlyOne(
            "android:pathData attribute in the bird asset",
            PATH_DATA_ATTRIBUTE.findAll(xml).map { it.groupValues[1] }.toList(),
        )
    if (!isSvgPathData(data)) {
        throw PatchException("NewX top-bar logo: the bird asset pathData is not a plain path-data string: $data")
    }
    return data
}

/** Control-point bounding box of the bird path, as [minX, maxX, minY, maxY]. */
internal fun birdBounds(pathData: String): FloatArray {
    val tokens = PATH_TOKEN.findAll(pathData).map { it.value }.toList()
    var index = 0
    var command = ' '
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    fun number(): Float {
        if (index >= tokens.size || tokens[index][0].isLetter()) {
            throw PatchException("NewX top-bar logo: path data ends inside command '$command'")
        }
        return tokens[index++].toFloat()
    }

    fun extend(px: Float, py: Float) {
        minX = minOf(minX, px)
        maxX = maxOf(maxX, px)
        minY = minOf(minY, py)
        maxY = maxOf(maxY, py)
    }

    while (index < tokens.size) {
        if (tokens[index][0].isLetter()) {
            command = tokens[index++][0]
        } else if (command == ' ') {
            throw PatchException("NewX top-bar logo: path data has a number before any command")
        }
        when (command) {
            'M', 'm' -> {
                val dx = number()
                val dy = number()
                x = if (command == 'M') dx else x + dx
                y = if (command == 'M') dy else y + dy
                startX = x
                startY = y
                extend(x, y)
                command = if (command == 'M') 'L' else 'l'
            }
            'L', 'l' -> {
                val dx = number()
                val dy = number()
                x = if (command == 'L') dx else x + dx
                y = if (command == 'L') dy else y + dy
                extend(x, y)
            }
            'H', 'h' -> {
                val dx = number()
                x = if (command == 'H') dx else x + dx
                extend(x, y)
            }
            'V', 'v' -> {
                val dy = number()
                y = if (command == 'V') dy else y + dy
                extend(x, y)
            }
            'C', 'c' -> {
                val originX = if (command == 'C') 0f else x
                val originY = if (command == 'C') 0f else y
                repeat(3) {
                    val px = originX + number()
                    val py = originY + number()
                    extend(px, py)
                    x = px
                    y = py
                }
            }
            'Z', 'z' -> {
                x = startX
                y = startY
                command = ' '
            }
            else -> throw PatchException("NewX top-bar logo: unsupported path command '$command' in the bird asset")
        }
    }
    if (minX > maxX || minY > maxY) throw PatchException("NewX top-bar logo: the bird asset path has no points")
    return floatArrayOf(minX, maxX, minY, maxY)
}

/** Stroke width that lets the two arm sweeps cover the whole glyph: ceil(0.7 * larger control-point extent). */
internal fun birdStrokeThickness(pathData: String): Float {
    val bounds = birdBounds(pathData)
    return ceil(STROKE_COVERAGE_FACTOR * max(bounds[1] - bounds[0], bounds[3] - bounds[2]))
}

/**
 * Sweep arms for the rising bird: the bird fills from the bottom up, like liquid, along a sweep line tilted
 * [BIRD_RISE_TILT_DEGREES] from vertical toward the beak. Every arm gets the same thickness, the ceiling of the bird's extent
 * perpendicular to the sweep line, so one arm's stroke covers the whole bird across. The extent along the sweep line is
 * split into [count] equal slices, bottom arm first, and each arm runs along its slice from tail to beak.
 *
 * The arm factory extends both ends of every arm by [BIRD_ARM_END_EXTENSION], so each arm is inset by that amount here.
 * With the extension added back, consecutive arms meet exactly at the slice boundaries and the outer arms end at the
 * bird's bounding box. Slices of [2 * BIRD_ARM_END_EXTENSION] units or less cannot hold an arm, so they fail.
 */
internal fun birdSweepArms(pathData: String, count: Int): List<SweepArm> {
    if (count < 1) throw PatchException("NewX top-bar logo: the bird sweep needs at least one arm, found $count")
    val bounds = birdBounds(pathData)
    val width = (bounds[1] - bounds[0]).toDouble()
    val height = (bounds[3] - bounds[2]).toDouble()
    val centerX = (bounds[0] + bounds[1]) / 2.0
    val centerY = (bounds[2] + bounds[3]) / 2.0
    val theta = Math.toRadians(BIRD_RISE_TILT_DEGREES)
    val tiltCos = cos(theta)
    val tiltSin = sin(theta)
    // Bounding-box extent perpendicular to the sweep line (the stroke thickness) and along it (split across the arms).
    val perpendicular = width * tiltCos + height * tiltSin
    val along = width * tiltSin + height * tiltCos
    val slice = along / count
    if (slice <= 2 * BIRD_ARM_END_EXTENSION) {
        throw PatchException(
            "NewX top-bar logo: cannot split the bird sweep into $count arms; each needs more than ${2 * BIRD_ARM_END_EXTENSION} units",
        )
    }
    val extension = BIRD_ARM_END_EXTENSION.toDouble()
    val thickness = ceil(perpendicular).toFloat()
    // Unit direction of the sweep line: up and slightly toward the beak (screen y grows downward).
    val directionX = tiltSin
    val directionY = -tiltCos
    return List(count) { arm ->
        val start = -along / 2 + arm * slice + extension
        val end = -along / 2 + (arm + 1) * slice - extension
        SweepArm(
            thickness = thickness,
            ax = (centerX + start * directionX).toFloat(),
            ay = (centerY + start * directionY).toFloat(),
            bx = (centerX + end * directionX).toFloat(),
            by = (centerY + end * directionY).toFloat(),
        )
    }
}

private fun readBirdAsset(): String =
    (
        object {}.javaClass.getResourceAsStream(BIRD_ASSET)
            ?: throw PatchException("NewX top-bar logo: bird asset $BIRD_ASSET is missing from the patch bundle")
    ).use { it.readBytes().toString(Charsets.UTF_8) }

private fun scanXLogoMethod(classType: String, method: Method): List<LogoHit> {
    val instructions = method.implementation?.instructions?.toList() ?: return emptyList()
    val owner = LogoMethodKey(classType, method.name, method.parameterTypes.map(CharSequence::toString))
    val hits = mutableListOf<LogoHit>()
    instructions.forEachIndexed { index, instruction ->
        when (instruction.opcode) {
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO -> {
                val value = ((instruction as ReferenceInstruction).reference as? StringReference)?.string
                if (value != null && isSvgPathData(value)) {
                    hits += LogoHit.Glyph(owner, index, (instruction as OneRegisterInstruction).registerA)
                }
            }
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE -> {
                val reference = (instruction as ReferenceInstruction).reference as? MethodReference ?: return@forEachIndexed
                if (reference.isFloatToRawIntBits()) {
                    hits += LogoHit.Coordinate(owner, index, argumentRegister(instruction))
                }
                if (reference.definingClass == classType &&
                    reference.parameterTypes.map(CharSequence::toString) == ARM_FACTORY_PARAMETERS &&
                    reference.returnType.startsWith(XLOGO_PACKAGE)
                ) {
                    hits += strokeHit(owner, instructions, index, instruction)
                }
            }
            else -> Unit
        }
    }
    return hits
}

/** `java.lang.Float.floatToRawIntBits(float)`: the call that packs one arm coordinate into its long. */
private fun MethodReference.isFloatToRawIntBits(): Boolean =
    definingClass == "Ljava/lang/Float;" &&
        name == "floatToRawIntBits" &&
        parameterTypes.map(CharSequence::toString) == listOf("F") &&
        returnType == "I"

/** Register of an invoke's first argument: the C slot of a five-register call, or the start of a range call. */
private fun argumentRegister(call: Instruction): Int =
    when (call) {
        is FiveRegisterInstruction -> call.registerC
        is RegisterRangeInstruction -> call.startRegister
        else -> throw PatchException("NewX top-bar logo: unexpected invoke encoding ${call.opcode}")
    }

private fun strokeHit(
    owner: LogoMethodKey,
    instructions: List<Instruction>,
    callIndex: Int,
    call: Instruction,
): LogoHit.Stroke {
    val register = argumentRegister(call)
    for (index in callIndex - 1 downTo 0) {
        val candidate = instructions[index]
        if ((candidate as? OneRegisterInstruction)?.registerA != register) continue
        val thickness =
            if (candidate.opcode in INTEGER_LITERAL_OPCODES) {
                Float.fromBits((candidate as NarrowLiteralInstruction).narrowLiteral)
            } else {
                Float.NaN
            }
        if (thickness in 0.5f..64f) return LogoHit.Stroke(owner, index, callIndex, register)
        throw PatchException(
            "NewX top-bar logo: the thickness argument v$register of the arm-factory call at ${owner.name}[$callIndex] " +
                "is not written by a float literal const (found ${candidate.opcode}).",
        )
    }
    throw PatchException("NewX top-bar logo: the thickness argument v$register of ${owner.name}[$callIndex] has no writer in the method")
}

context(context: BytecodePatchContext)
private fun scanLogo(): List<LogoHit> =
    classDefFlatMap { classDef ->
        val type = classDef.type
        when {
            type.startsWith(REFRESH_PACKAGE) -> listOf(LogoHit.RefreshClass(type))
            type.startsWith(XLOGO_PACKAGE) ->
                listOf<LogoHit>(LogoHit.XLogoClass(type)) + classDef.methods.flatMap { scanXLogoMethod(type, it) }
            else -> emptyList()
        }
    }

context(context: BytecodePatchContext)
private fun mutableLogoMethod(key: LogoMethodKey): MutableMethod =
    requireExactlyOne(
        "NewX top-bar logo method ${key.name} in ${key.classType}",
        context.mutableClassDefBy(key.classType).methods.filter { method ->
            method.name == key.name && method.parameterTypes.map(CharSequence::toString) == key.parameterTypes
        },
    )

/** Overwrites the register the original path-data `const-string` just wrote, before its consumer reads it. */
internal fun MutableMethod.replaceGlyphPath(site: LogoHit.Glyph, pathData: String) {
    insertHook(site.index + 1, relocateBranchTargets = true) { constString(site.register, pathData) }
}

/** Overwrites the thickness register right after its float literal, before the arm-factory call reads it. */
internal fun MutableMethod.replaceStrokeThickness(site: LogoHit.Stroke, arm: SweepArm) {
    insertHook(site.defIndex + 1, relocateBranchTargets = true) { constInt(site.register, arm.thickness.toRawBits()) }
}

/**
 * Overwrites a coordinate register right before its float-to-bits call reads it. The arms share registers, so each call
 * gets its own write instead of relying on the one before the literal.
 */
internal fun MutableMethod.replaceCoordinate(site: LogoHit.Coordinate, value: Float) {
    insertHook(site.callIndex, relocateBranchTargets = true) { constInt(site.register, value.toRawBits()) }
}

/** [index] is the real insertion index in the original method; edits are applied from the highest index down. */
private class LogoEdit(val owner: LogoMethodKey, val index: Int, val apply: (MutableMethod) -> Unit)

/**
 * Draws the Twitter bird in the NewX animated top-bar logo (12.32.0-alpha.04+). The glyph path-data constant is swapped for
 * the bird asset, and the two arm sweeps are replaced by tilted, horizontal-ish bands stacked from the bottom of the bird
 * upward, so the reveal fills the bird bottom to top. Pre-procedural APKs are a no-op.
 */
val restoreTwitterTopBarLogoPatch =
    bytecodePatch {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val sites = requireLogoSites(scanLogo()) ?: return@execute
            val pathData = birdPathData(readBirdAsset())
            val arms = birdSweepArms(pathData, sites.strokes.size)

            val edits = mutableListOf<LogoEdit>()
            sites.strokes.forEachIndexed { arm, stroke ->
                val sweep = arms[arm]
                edits += LogoEdit(stroke.owner, stroke.defIndex + 1) { it.replaceStrokeThickness(stroke, sweep) }
                val values = listOf(sweep.ax, sweep.ay, sweep.bx, sweep.by)
                sites.coordinates[arm].zip(values).forEach { (site, value) ->
                    edits += LogoEdit(site.owner, site.callIndex) { it.replaceCoordinate(site, value) }
                }
            }
            edits += LogoEdit(sites.glyph.owner, sites.glyph.index + 1) { it.replaceGlyphPath(sites.glyph, pathData) }

            edits.groupBy { it.owner }.forEach { (owner, ownerEdits) ->
                ownerEdits.groupBy { it.index }.forEach { (index, sameIndex) ->
                    if (sameIndex.size > 1) {
                        throw PatchException(
                            "NewX top-bar logo: ${sameIndex.size} edits insert at ${owner.name}[$index]; the sites overlap",
                        )
                    }
                }
                val method = mutableLogoMethod(owner)
                ownerEdits.sortedByDescending { it.index }.forEach { it.apply(method) }
            }
        }
    }
