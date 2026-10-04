package app.crimera.patches.newx.misc.profiletabs

// Guards the profile-tab hook emission: the filter must run in the register that already holds
// listOfNotNull's result (no scratch allocation in the 234-register builder frame), the invoke and
// its move-result must stay paired, an incoming branch has to land on the hook head instead of
// skipping the filter, and the tabId bridge must check-cast the Object parameter before the
// release-specific field read.

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileTabsHookTest {
    private val filterDescriptor =
        "Lapp/morphe/extension/newx/misc/ProfileTabFilter;->filter(Ljava/util/List;)Ljava/util/List;"

    @Test
    fun `filter replaces the list in place and keeps the consumer call`() {
        val method = consumerFixture()
        val consumer = method.instructions[4]
        val insertionIndex = method.instructions.indexOf(consumer)

        val insertion = injectProfileTabFilterHook(method, insertionIndex, listRegister = 3)

        assertEquals(insertionIndex, insertion.firstIndex)
        assertEquals(insertionIndex + 1, insertion.lastIndex)
        assertTrue(insertion.registers.isEmpty(), "the hook must not allocate a scratch register")

        val instructions = method.instructions
        assertFilterCall(instructions[insertionIndex], listRegister = 3)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[insertionIndex + 1].opcode)
        assertEquals(3, (instructions[insertionIndex + 1] as OneRegisterInstruction).registerA)
        assertSame(consumer, instructions[insertionIndex + 2])
    }

    @Test
    fun `branch targeting the consumer is relocated onto the hook`() {
        val method = consumerFixture()
        val consumer = method.instructions[4]
        method.addInstructionsWithLabels(
            0,
            """
                const/4 v0, 0x0
                if-eqz v0, :consumer
            """.trimIndent(),
            ExternalLabel("consumer", consumer),
        )
        val insertionIndex = method.instructions.indexOf(consumer)

        val insertion = injectProfileTabFilterHook(method, insertionIndex, listRegister = 3)

        val branch = method.instructions[1] as BuilderOffsetInstruction
        assertEquals(
            insertion.firstIndex,
            branch.target.location.index,
            "a path that used to reach the consumer must run the filter first",
        )
        assertFilterCall(method.instructions[insertion.firstIndex], listRegister = 3)
    }

    private fun assertFilterCall(
        instruction: Instruction,
        listRegister: Int,
    ) {
        assertEquals(Opcode.INVOKE_STATIC, instruction.opcode)
        val reference = instruction.getReference<MethodReference>()
        assertEquals(filterDescriptor, reference?.let { "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" })
        assertEquals(listOf(listRegister), instruction.registersUsed)
    }

    @Test
    fun `tab id bridge casts the page config before the field read`() {
        val method = tabIdBridgeFixture()
        val pageConfigType = "Lcom/x/profile/FixturePage;"
        val tabField = ImmutableFieldReference(pageConfigType, "a", "Lcom/x/profile/FixtureTab;")
        val p0 = method.p0Register

        method.injectProfileTabIdBridgeBody(tabField)

        val instructions = method.instructions
        assertEquals(Opcode.CHECK_CAST, instructions[0].opcode)
        assertEquals(p0, (instructions[0] as OneRegisterInstruction).registerA)
        assertEquals(
            pageConfigType,
            (instructions[0] as ReferenceInstruction).getReference<TypeReference>()?.toString(),
        )
        assertEquals(Opcode.IGET_OBJECT, instructions[1].opcode)
        assertEquals(p0, (instructions[1] as TwoRegisterInstruction).registerA)
        assertEquals(p0, (instructions[1] as TwoRegisterInstruction).registerB)
        assertEquals(Opcode.INVOKE_VIRTUAL, instructions[2].opcode)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[3].opcode)
        assertEquals(p0, (instructions[3] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.RETURN_OBJECT, instructions[4].opcode)
        assertEquals(p0, (instructions[4] as OneRegisterInstruction).registerA)
    }

    private fun tabIdBridgeFixture(): MutableMethod {
        val implementation = MethodImplementationBuilder(2)
        implementation.addInstruction("const/4 v0, 0x0".toInstruction())
        implementation.addInstruction("return-object v0".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/morphe/extension/newx/misc/ProfileTabsCatalog;",
                "tabIdOf",
                listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }

    /** `filled-new-array` / `move-result-object` / `listOfNotNull` / `move-result-object` / consumer. */
    private fun consumerFixture(): MutableMethod {
        val method = fixture()
        method.addInstructionsWithLabels(
            0,
            """
                filled-new-array/range {v1 .. v1}, [Lcom/x/profile/FixtureTab;
                move-result-object v2
                invoke-static {v2}, Lkotlin/collections/CollectionsKt;->listOfNotNull([Ljava/lang/Object;)Ljava/util/List;
                move-result-object v3
                invoke-static {v1, v3}, Lapp/crimera/test/Consumer;->consume(Ljava/lang/Object;Ljava/util/List;)V
            """.trimIndent(),
        )
        return method
    }

    private fun fixture(registerCount: Int = 8): MutableMethod {
        val implementation = MethodImplementationBuilder(registerCount)
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/ProfileTabsFixture;",
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
