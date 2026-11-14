package app.crimera.patches.newx.utils

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Guards the patch-time flag hook's invoke descriptor, boolean value encoding, and 4-bit arguments.
class FeatureFlagsTest {
    @Test
    fun `patch helper registers the requested flag value at application startup`() {
        val method = applicationOnCreateFixture()

        method.registerNewXFeatureFlag("x_home_nav_enabled", enabled = false)

        val instructions = method.instructions
        assertEquals(Opcode.CONST_STRING, instructions[0].opcode)
        val flagRegister = (instructions[0] as OneRegisterInstruction).registerA
        assertEquals("x_home_nav_enabled", (instructions[0] as ReferenceInstruction).reference.let {
            (it as StringReference).string
        })

        assertTrue(instructions[1].opcode in setOf(Opcode.CONST_4, Opcode.CONST_16))
        val enabledRegister = (instructions[1] as OneRegisterInstruction).registerA
        assertEquals(0, (instructions[1] as NarrowLiteralInstruction).narrowLiteral)
        assertEquals(Opcode.INVOKE_STATIC, instructions[2].opcode)
        val reference = (instructions[2] as ReferenceInstruction).reference as MethodReference
        assertEquals(
            "Lapp/morphe/extension/newx/featureswitches/FeatureSwitchStore;->" +
                "registerPatchOverride(Ljava/lang/String;Z)V",
            "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}",
        )
        assertEquals(listOf(flagRegister, enabledRegister), instructions[2].registersUsed)
        assertTrue(flagRegister < 16 && enabledRegister < 16, "invoke arguments must fit 4-bit registers")
        assertEquals(Opcode.RETURN_VOID, instructions[3].opcode)
    }

    private fun applicationOnCreateFixture(): MutableMethod {
        val implementation = MethodImplementationBuilder(16)
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/Application;",
                "onCreate",
                emptyList(),
                "V",
                AccessFlags.PUBLIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
