package app.crimera.patches.newx.misc.bringbacktwitter

// Guards the NewX top-bar logo patch: a branding patch that applies but leaves the animated top-bar
// logo unchanged on xlogo APKs.

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RestoreTwitterTopBarLogoPatchTest {
    private val birdAsset = "/twitter/bringbacktwitter/drawable/ic_vector_twitter.xml"
    private val xlogoClass = LogoHit.XLogoClass("Lcom/x/ui/common/xlogo/XLogoView;")
    private val refreshClass = LogoHit.RefreshClass("Lcom/x/home/tabbed/refresh/RefreshHeader;")
    private val owner = LogoMethodKey("Lcom/x/ui/common/xlogo/XLogoView;", "draw", emptyList())
    private val armOwner = LogoMethodKey("Lcom/x/ui/common/xlogo/XLogoView;", "arm", listOf("F", "J", "J"))

    @Test
    fun `contract gate`() {
        assertNull(requireLogoSites(emptyList()))

        val refreshOnly = assertFailsWith<PatchException> { requireLogoSites(listOf(refreshClass)) }
        assertTrue(refreshOnly.message.orEmpty().contains("xlogo"), refreshOnly.message)

        assertFailsWith<PatchException> { requireLogoSites(listOf(xlogoClass)) }
        assertFailsWith<PatchException> {
            requireLogoSites(listOf(xlogoClass, glyph(index = 0), glyph(index = 4)))
        }
        assertFailsWith<PatchException> { requireLogoSites(listOf(xlogoClass, glyph(index = 0))) }

        // Each arm needs exactly four coordinate calls between the previous arm-factory call and its own.
        val arm = stroke(defIndex = 8, register = 5, callIndex = 9)
        val threeCoordinates = listOf(xlogoClass, glyph(index = 0), arm) + listOf(1, 3, 5).map { coordinate(it) }
        val threeFailure = assertFailsWith<PatchException> { requireLogoSites(threeCoordinates) }
        assertTrue(threeFailure.message.orEmpty().contains("expected 4"), threeFailure.message)

        val sites = assertNotNull(requireLogoSites(threeCoordinates + coordinate(callIndex = 7)))
        assertEquals(1, sites.strokes.size)
        assertEquals(4, sites.coordinates.single().size)
    }

    @Test
    fun `bird asset`() {
        val xml = readBirdXml()

        val pathData = birdPathData(xml)
        assertTrue(pathData.startsWith("M"), pathData)
        assertTrue(isSvgPathData(pathData), pathData)
        assertTrue(birdStrokeThickness(pathData) in 10f..30f, "stroke ${birdStrokeThickness(pathData)}")

        assertFailsWith<PatchException> {
            birdPathData(
                """
                <vector>
                    <path android:pathData="M0 0L1 1Z" />
                    <path android:pathData="M2 2L3 3Z" />
                </vector>
                """.trimIndent(),
            )
        }
        assertFailsWith<PatchException> { birdStrokeThickness("M0 0A1 1 0 0 1 2 2Z") }
    }

    @Test
    fun `glyph emission`() {
        val method = fixture(registerCount = 8)
        method.addInstructionsWithLabels(
            0,
            """
                const-string v7, "M0 0H10V10Z"
                invoke-virtual {v7}, Lapp/crimera/test/Sink;->accept(Ljava/lang/String;)V
            """.trimIndent(),
        )
        val originalConst = method.instructions[0]
        val invoke = method.instructions[1]
        // Branch into the invoke. It is placed after the invoke so the invoke keeps index 2 in the
        // expected layout (const-string, new const-string, invoke, branch, return-void).
        method.addInstructionsWithLabels(2, "if-eqz v7, :invoke", ExternalLabel("invoke", invoke))

        method.replaceGlyphPath(LogoHit.Glyph(owner, index = 0, register = 7), "M1 1H5V5Z")

        val instructions = method.instructions
        assertSame(originalConst, instructions[0])
        assertEquals(Opcode.CONST_STRING, instructions[1].opcode)
        assertEquals(7, (instructions[1] as OneRegisterInstruction).registerA)
        assertEquals("M1 1H5V5Z", instructions[1].stringLiteral())
        assertSame(invoke, instructions[2])

        val branch = instructions[3] as BuilderOffsetInstruction
        assertEquals(Opcode.IF_EQZ, branch.opcode)
        assertEquals(
            instructions.indexOf(instructions[1]),
            branch.target.location.index,
            "the branch into the invoke must land on the new const-string head",
        )
    }

    @Test
    fun `stroke emission`() {
        val method = fixture(registerCount = 10)
        method.addInstructionsWithLabels(
            0,
            """
                const v9, 0x40b33333
                invoke-static {v9}, Lapp/crimera/test/Arm;->sweep(F)V
                const v5, 0x4019999a
                invoke-static {v5}, Lapp/crimera/test/Arm;->sweep(F)V
            """.trimIndent(),
        )
        val constEarly = method.instructions[0]
        val invokeEarly = method.instructions[1]
        val constLate = method.instructions[2]
        val invokeLate = method.instructions[3]

        method.replaceStrokeThickness(LogoHit.Stroke(armOwner, defIndex = 2, callIndex = 3, register = 5), sweep(16f))
        method.replaceStrokeThickness(LogoHit.Stroke(armOwner, defIndex = 0, callIndex = 1, register = 9), sweep(16f))

        val instructions = method.instructions
        assertEquals(7, instructions.size)
        assertSame(constEarly, instructions[0])
        assertHighConst(instructions[1], register = 9)
        assertSame(invokeEarly, instructions[2])
        assertSame(constLate, instructions[3])
        assertHighConst(instructions[4], register = 5)
        assertSame(invokeLate, instructions[5])

        // A coordinate write lands directly before its float-to-bits call, which itself is left unchanged.
        val coordinateMethod = fixture(registerCount = 4)
        coordinateMethod.addInstructionsWithLabels(
            0,
            """
                const v2, 0x41ac0000
                invoke-static {v2}, Ljava/lang/Float;->floatToRawIntBits(F)I
            """.trimIndent(),
        )
        val constOriginal = coordinateMethod.instructions[0]
        val invokeOriginal = coordinateMethod.instructions[1]

        coordinateMethod.replaceCoordinate(LogoHit.Coordinate(armOwner, callIndex = 1, register = 2), 2f)

        val coordinateInstructions = coordinateMethod.instructions
        assertEquals(4, coordinateInstructions.size)
        assertSame(constOriginal, coordinateInstructions[0])
        assertEquals(Opcode.CONST_HIGH16, coordinateInstructions[1].opcode)
        assertEquals(2, (coordinateInstructions[1] as OneRegisterInstruction).registerA)
        assertEquals(2f.toRawBits(), (coordinateInstructions[1] as NarrowLiteralInstruction).narrowLiteral)
        assertSame(invokeOriginal, coordinateInstructions[2])
    }

    @Test
    fun `sweep geometry`() {
        val pathData = birdPathData(readBirdXml())
        val arms = birdSweepArms(pathData, 2)
        assertEquals(2, arms.size)
        assertEquals(27f, arms[0].thickness)
        assertEquals(27f, arms[1].thickness)

        // Both arms run along the sweep line: 15 degrees from vertical, toward the beak.
        val tilt = Math.toRadians(15.0)
        val dirX = sin(tilt).toFloat()
        val dirY = -cos(tilt).toFloat()
        arms.forEach { arm ->
            val dx = arm.bx - arm.ax
            val dy = arm.by - arm.ay
            val length = sqrt(dx * dx + dy * dy)
            assertEquals(dirX, dx / length, 1e-3f)
            assertEquals(dirY, dy / length, 1e-3f)
        }
        val (first, second) = arms
        // Bottom arm first: it starts lower on screen, so it has the larger y.
        assertTrue(first.ay > second.ay, "arm 0 should be the bottom arm: ${first.ay} vs ${second.ay}")
        // Each arm is inset by 2 at both ends, so the two insets leave a 4-unit gap along the sweep line.
        val gap = (second.ax - first.bx) * dirX + (second.ay - first.by) * dirY
        assertEquals(4f, gap, 0.01f)
        // One stroke must cover the bird's full extent across the sweep line, or the reveal stays partial.
        val bounds = birdBounds(pathData)
        val perpendicular = (bounds[1] - bounds[0]) * cos(tilt).toFloat() + (bounds[3] - bounds[2]) * sin(tilt).toFloat()
        assertTrue(first.thickness >= perpendicular, "thickness ${first.thickness} is below the extent $perpendicular")

        assertFailsWith<PatchException> { birdSweepArms(pathData, 0) }
        val tooMany = assertFailsWith<PatchException> { birdSweepArms(pathData, 6) }
        assertTrue(tooMany.message.orEmpty().contains("more than 4"), tooMany.message)
    }

    private fun glyph(index: Int) = LogoHit.Glyph(owner, index, register = 7)

    private fun stroke(
        defIndex: Int,
        register: Int,
        callIndex: Int = defIndex + 1,
    ) = LogoHit.Stroke(armOwner, defIndex, callIndex, register)

    private fun coordinate(callIndex: Int) = LogoHit.Coordinate(armOwner, callIndex, register = 5)

    private fun sweep(thickness: Float) = SweepArm(thickness, ax = 0f, ay = 0f, bx = 0f, by = 0f)

    private fun readBirdXml(): String =
        requireNotNull(RestoreTwitterTopBarLogoPatchTest::class.java.getResourceAsStream(birdAsset)) {
            "missing $birdAsset on the test classpath"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun Instruction.stringLiteral(): String? = getReference<StringReference>()?.string

    private fun assertHighConst(
        instruction: Instruction,
        register: Int,
    ) {
        assertEquals(Opcode.CONST_HIGH16, instruction.opcode)
        assertEquals(register, (instruction as OneRegisterInstruction).registerA)
        assertEquals(0x41800000, (instruction as NarrowLiteralInstruction).narrowLiteral)
    }

    /** Empty void static method; test bodies are prepended with `addInstructionsWithLabels`. */
    private fun fixture(registerCount: Int): MutableMethod {
        val implementation = MethodImplementationBuilder(registerCount)
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/RestoreTwitterTopBarLogoFixture;",
                "builder",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
