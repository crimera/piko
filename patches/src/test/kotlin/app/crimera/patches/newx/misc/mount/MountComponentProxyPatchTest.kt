package app.crimera.patches.newx.misc.mount

// Guards the 12.40.1-prod.01 root-mount regression: the manifest's CoreComponentFactory keeps only
// <init> in dex (R8 removed its overrides), so the old hook found no callback to patch and silently
// skipped, and Piko settings opened the stock proxy activity (PerfDiagnosticsActivity) instead.
// These tests prove a missing callback is added with the resolve hook ahead of the super call, and
// that an existing callback receives the same hook in front of its original body.

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MountComponentProxyPatchTest {
    private val factoryType = "Landroidx/core/app/CoreComponentFactory;"

    @Test
    fun `a factory without the callback gains an override that resolves before calling super`() {
        assertEquals(
            listOf(
                Triple("instantiateActivity", "Landroid/app/Activity;", "resolveActivityClassName"),
                Triple("instantiateReceiver", "Landroid/content/BroadcastReceiver;", "resolveReceiverClassName"),
            ),
            factoryCallbacks.map { Triple(it.name, it.returnType, it.resolverName) },
        )

        factoryCallbacks.forEach { callback ->
            val factory = factoryWithOnlyInit()
            val added = factory.addFactoryCallbackOverride(callback, FRAMEWORK_FACTORY_DESCRIPTOR)

            assertEquals(2, factory.methods.size, "${callback.name}: <init> plus exactly one override")
            assertTrue(factory.methods.contains(added), "${callback.name}: returned method must be the added one")
            assertEquals(callback.name, added.name)
            assertEquals(callback.returnType, added.returnType)
            assertEquals(
                listOf("Ljava/lang/ClassLoader;", "Ljava/lang/String;", "Landroid/content/Intent;"),
                added.parameterTypes.map(CharSequence::toString),
            )
            assertTrue(added.accessFlags and AccessFlags.PUBLIC.value != 0, "${callback.name} must be public")
            assertEquals(0, added.accessFlags and AccessFlags.FINAL.value, "${callback.name} must not be final")
            assertEquals(0, added.accessFlags and AccessFlags.SYNTHETIC.value, "${callback.name} must not be synthetic")

            assertEquals(4, added.implementation!!.registerCount, "${callback.name}: p0..p3 only, no scratch")
            val instructions = added.instructions.toList()
            assertEquals(
                listOf(
                    Opcode.INVOKE_STATIC,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.INVOKE_SUPER,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.RETURN_OBJECT,
                ),
                instructions.map { it.opcode },
                "${callback.name}: unexpected override body",
            )

            assertResolveHook(callback, instructions[0], instructions[1])

            val superCall = instructions[2]
            assertEquals(
                "Landroid/app/AppComponentFactory;->${callback.name}" +
                    "(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Intent;)${callback.returnType}",
                superCall.getReference<MethodReference>().toString(),
            )
            assertEquals(listOf(0, 1, 2, 3), superCall.registers())

            assertEquals(1, (instructions[3] as OneRegisterInstruction).registerA, "super result goes to p1")
            assertEquals(1, (instructions[4] as OneRegisterInstruction).registerA, "p1 is returned")
        }
    }

    @Test
    fun `an existing callback keeps its body behind the resolve hook`() {
        factoryCallbacks.forEach { callback ->
            val method = existingOverride(callback)
            val registersBefore = method.implementation!!.registerCount
            method.insertResolveClassNameHook(callback)

            assertEquals(registersBefore, method.implementation!!.registerCount, "${callback.name}: frame must not change")
            val instructions = method.instructions.toList()
            assertEquals(
                listOf(
                    Opcode.INVOKE_STATIC,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.INVOKE_SUPER,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.RETURN_OBJECT,
                ),
                instructions.map { it.opcode },
                "${callback.name}: hook must be a 2-instruction prefix before the original body",
            )

            // 6 registers: p0 = v2.
            val p0 = registersBefore - 4
            assertResolveHook(callback, instructions[0], instructions[1], p0)
            assertEquals(
                listOf(p0, p0 + 1, p0 + 2, p0 + 3),
                instructions[2].registers(),
                "the original super call must be preserved",
            )
            assertEquals(0, (instructions[3] as OneRegisterInstruction).registerA)
            assertEquals(0, (instructions[4] as OneRegisterInstruction).registerA)
        }
    }

    // Guards the 12.40.1 root-mount abort: hooking a compiled androidx CoreComponentFactory override
    // that declares no locals (.registers 4, p0..p3 only) must not abort the mount patch, because
    // a PatchException there takes every dependent patch (Settings, Crash logs, Inline download)
    // down with it. The hook needs no scratch register, so the frame must stay exactly as it was.
    @Test
    fun `a callback with no locals is hooked without aborting the patch`() {
        factoryCallbacks.forEach { callback ->
            val method = zeroLocalsOverride(callback)
            assertEquals(4, method.implementation!!.registerCount, "fixture must declare zero locals")

            try {
                method.insertResolveClassNameHook(callback)
            } catch (exception: PatchException) {
                fail("${callback.name}: hooking a zero-locals override must succeed, but threw: ${exception.message}")
            }

            assertEquals(4, method.implementation!!.registerCount, "${callback.name}: frame must stay unchanged")
            val instructions = method.instructions.toList()
            assertEquals(
                listOf(
                    Opcode.INVOKE_STATIC,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.INVOKE_SUPER,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.INVOKE_STATIC,
                    Opcode.MOVE_RESULT_OBJECT,
                    Opcode.CHECK_CAST,
                    Opcode.RETURN_OBJECT,
                ),
                instructions.map { it.opcode },
                "${callback.name}: hook prefix plus the original six-instruction body",
            )

            assertResolveHook(callback, instructions[0], instructions[1])

            // The original tail is intact with the same registers.
            assertEquals(listOf(0, 1, 2, 3), instructions[2].registers())
            assertEquals(1, (instructions[3] as OneRegisterInstruction).registerA)
            assertEquals(listOf(1), instructions[4].registers())
            assertEquals(1, (instructions[5] as OneRegisterInstruction).registerA)
            assertEquals(1, (instructions[6] as OneRegisterInstruction).registerA)
            assertEquals(1, (instructions[7] as OneRegisterInstruction).registerA)
        }
    }

    /** `invoke-static {p2, p3}, callback.resolver` then `move-result-object p2` (p0 = [p0]). */
    private fun assertResolveHook(
        callback: FactoryCallback,
        invoke: Instruction,
        moveResult: Instruction,
        p0: Int = 0,
    ) {
        assertEquals(Opcode.INVOKE_STATIC, invoke.opcode, "${callback.name}: resolver call")
        val reference = invoke.getReference<MethodReference>()!!
        assertEquals(callback.resolver.definingClass, reference.definingClass)
        assertEquals(callback.resolverName, reference.name)
        assertEquals(callback.resolver.name, reference.name)
        assertEquals(listOf("Ljava/lang/String;", "Landroid/content/Intent;"), reference.parameterTypes.map(CharSequence::toString))
        assertEquals("Ljava/lang/String;", reference.returnType)
        assertEquals(callback.resolver.toString(), reference.toString())
        assertEquals(listOf(p0 + 2, p0 + 3), invoke.registers(), "${callback.name}: resolver takes (className, intent)")

        assertEquals(Opcode.MOVE_RESULT_OBJECT, moveResult.opcode)
        assertEquals(p0 + 2, (moveResult as OneRegisterInstruction).registerA, "the resolved name must replace p2")
    }

    private fun Instruction.registers(): List<Int> {
        val five = this as FiveRegisterInstruction
        return listOf(five.registerC, five.registerD, five.registerE, five.registerF, five.registerG)
            .take(five.registerCount)
    }

    private fun factoryWithOnlyInit(): MutableClass {
        val implementation = MethodImplementationBuilder(1)
        implementation.addInstruction("return-void".toInstruction())
        val init =
            ImmutableMethod(
                factoryType,
                "<init>",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            )
        return MutableClass(
            ImmutableClassDef(
                factoryType,
                AccessFlags.PUBLIC.value,
                FRAMEWORK_FACTORY_DESCRIPTOR,
                emptyList<String>(),
                null,
                emptySet<ImmutableAnnotation>(),
                emptyList<ImmutableField>(),
                listOf(init),
            ),
        )
    }

    /** `<callback>(ClassLoader, String, Intent)` with 2 locals, body: `super.<callback>(...)` then return it. */
    private fun existingOverride(callback: FactoryCallback): MutableMethod {
        val implementation = MethodImplementationBuilder(6)
        implementation.addInstruction("return-void".toInstruction())
        val method =
            MutableMethod(
                ImmutableMethod(
                    factoryType,
                    callback.name,
                    callback.parameters.map { ImmutableMethodParameter(it, emptySet<ImmutableAnnotation>(), null) },
                    callback.returnType,
                    AccessFlags.PUBLIC.value,
                    emptySet<ImmutableAnnotation>(),
                    emptySet<HiddenApiRestriction>(),
                    implementation.methodImplementation,
                ),
            )
        method.addInstructionsWithLabels(
            0,
            """
                invoke-super {v2, v3, v4, v5}, Landroid/app/AppComponentFactory;->${callback.name}(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Intent;)${callback.returnType}
                move-result-object v0
                return-object v0
            """.trimIndent(),
        )
        // Drop the placeholder return-void left behind the inserted body.
        method.removeInstruction(3)
        return method
    }

    /**
     * The shape androidx ships in `CoreComponentFactory`: `.registers 4` (ZERO locals; v0..v3 are
     * p0..p3) and the result of `super` is rewritten in place through p1:
     * `super` -> `move-result-object p1` -> `checkCompatWrapper(p1)` -> `check-cast` -> `return-object p1`.
     */
    private fun zeroLocalsOverride(callback: FactoryCallback): MutableMethod {
        val implementation = MethodImplementationBuilder(4)
        implementation.addInstruction("return-void".toInstruction())
        val method =
            MutableMethod(
                ImmutableMethod(
                    factoryType,
                    callback.name,
                    callback.parameters.map { ImmutableMethodParameter(it, emptySet<ImmutableAnnotation>(), null) },
                    callback.returnType,
                    AccessFlags.PUBLIC.value,
                    emptySet<ImmutableAnnotation>(),
                    emptySet<HiddenApiRestriction>(),
                    implementation.methodImplementation,
                ),
            )
        method.addInstructionsWithLabels(
            0,
            """
                invoke-super {p0, p1, p2, p3}, Landroid/app/AppComponentFactory;->${callback.name}(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Intent;)${callback.returnType}
                move-result-object p1
                invoke-static {p1}, Landroidx/core/app/CoreComponentFactory;->checkCompatWrapper(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p1
                check-cast p1, ${callback.returnType}
                return-object p1
            """.trimIndent(),
        )
        // Drop the placeholder return-void left behind the inserted body.
        method.removeInstruction(6)
        return method
    }
}
