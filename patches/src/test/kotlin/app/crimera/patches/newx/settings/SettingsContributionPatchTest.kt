package app.crimera.patches.newx.settings

import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsContributionPatchTest {
    @Test
    fun `toggle read emits four-bit boolean invocation`() {
        val method = mutableMethod(registerCount = 2, "return-void")

        val read =
            toggle.injectRead(
                method = method,
                index = 0,
                registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
            )

        assertEquals(0, read.register)
        assertEquals(3, read.nextIndex)
        assertSettingId(method.instructions[0], toggle.id, read.register)
        assertFourBitInvoke(method.instructions[1], "getBooleanOrDefault", "Z", read.register)
        assertMoveResult(method.instructions[2], Opcode.MOVE_RESULT, read.register)
    }

    @Test
    fun `text input read emits byte-range invocation and object move-result`() {
        val method = mutableMethod(registerCount = 17, "return-void")

        val read =
            textInput.injectRead(
                method = method,
                index = 0,
                excludedRegisters = (0..15).toList(),
            )

        assertEquals(16, read.register)
        assertEquals(3, read.nextIndex)
        assertSettingId(method.instructions[0], textInput.id, read.register)
        assertByteInvoke(method.instructions[1], "getStringOrDefault", "Ljava/lang/String;", read.register)
        assertMoveResult(method.instructions[2], Opcode.MOVE_RESULT_OBJECT, read.register)
    }

    @Test
    fun `multi-choice read honors exclusions and returns the next insertion index`() {
        val method = mutableMethod(registerCount = 3, "const/4 v0, 0x0", "return-void")

        val read =
            multiChoice.injectRead(
                method = method,
                index = 1,
                excludedRegisters = listOf(0),
            )

        assertEquals(1, read.register)
        assertEquals(4, read.nextIndex)
        assertSettingId(method.instructions[1], multiChoice.id, read.register)
        assertFourBitInvoke(method.instructions[2], "getStringSetOrDefault", "Ljava/util/Set;", read.register)
        assertMoveResult(method.instructions[3], Opcode.MOVE_RESULT_OBJECT, read.register)
        assertEquals(Opcode.RETURN_VOID, method.instructions[read.nextIndex].opcode)
    }

    @Test
    fun `group registration tracker emits compatible metadata once`() {
        val tracker = SettingsGroupRegistrationTracker()
        val group =
            SettingsGroupDefinition(
                id = "newx.test.group",
                titleResourceName = "piko_newx_test_group_title",
                summaryResourceName = null,
                iconResourceName = null,
                order = 0,
                children = listOf(toggle),
            )

        assertTrue(tracker.shouldEmit(null, group, category = true))
        assertFalse(tracker.shouldEmit(null, group, category = true))
        assertFailsWith<PatchException> {
            tracker.shouldEmit("newx.test.parent", group, category = false)
        }
    }

    @Test
    fun `setting read fails when every compatible register is excluded`() {
        val method = mutableMethod(registerCount = 1, "return-void")

        val exception =
            assertFailsWith<IllegalStateException> {
                toggle.injectRead(
                    method = method,
                    index = 0,
                    excludedRegisters = listOf(0),
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            }

        assertContains(exception.message.orEmpty(), "register available for NewX setting read")
        assertEquals(listOf(Opcode.RETURN_VOID), method.instructions.map(Instruction::getOpcode))
    }

    @Test
    fun `return-void guards branch to the original insertion instruction in the correct direction`() {
        assertReturnVoidGuard(Opcode.IF_EQZ) { method -> toggle.returnVoidIfEnabled(method, 0) }
        assertReturnVoidGuard(Opcode.IF_NEZ) { method -> toggle.returnVoidIfDisabled(method, 0) }
    }

    @Test
    fun `setting branches use the correct direction and supplied target label`() {
        assertSettingBranch(Opcode.IF_NEZ) { method, target ->
            toggle.branchIfEnabled(method, 0, target)
        }
        assertSettingBranch(Opcode.IF_EQZ) { method, target ->
            toggle.branchIfDisabled(method, 0, target)
        }
    }

    private fun assertReturnVoidGuard(
        expectedBranchOpcode: Opcode,
        inject: (MutableMethod) -> Unit,
    ) {
        val method = mutableMethod(registerCount = 2, "const/4 v0, 0x0", "return-void")
        val originalInsertionInstruction = method.instructions[0]

        inject(method)

        assertFourBitInvoke(method.instructions[1], "getBooleanOrDefault", "Z", register = 0)
        val branch = assertIs<BuilderOffsetInstruction>(method.instructions[3])
        assertEquals(expectedBranchOpcode, branch.opcode)
        assertEquals(0, assertIs<OneRegisterInstruction>(branch).registerA)
        assertEquals(Opcode.RETURN_VOID, method.instructions[4].opcode)
        assertEquals(5, branch.target.location.index)
        assertSame(originalInsertionInstruction, method.instructions[branch.target.location.index])
    }

    private fun assertSettingBranch(
        expectedBranchOpcode: Opcode,
        inject: (MutableMethod, Instruction) -> Unit,
    ) {
        val method =
            mutableMethod(
                registerCount = 3,
                "const/4 v0, 0x0",
                "const/4 v1, 0x1",
                "return-void",
            )
        val target = method.instructions[1]

        inject(method, target)

        assertFourBitInvoke(method.instructions[1], "getBooleanOrDefault", "Z", register = 0)
        val branch = assertIs<BuilderOffsetInstruction>(method.instructions[3])
        assertEquals(expectedBranchOpcode, branch.opcode)
        assertEquals(0, assertIs<OneRegisterInstruction>(branch).registerA)
        assertEquals(5, branch.target.location.index)
        assertSame(target, method.instructions[branch.target.location.index])
    }

    private fun assertSettingId(
        instruction: Instruction,
        expectedId: String,
        expectedRegister: Int,
    ) {
        assertEquals(Opcode.CONST_STRING, instruction.opcode)
        assertEquals(expectedRegister, assertIs<OneRegisterInstruction>(instruction).registerA)
        val reference = assertIs<StringReference>(assertIs<ReferenceInstruction>(instruction).reference)
        assertEquals(expectedId, reference.string)
    }

    private fun assertFourBitInvoke(
        instruction: Instruction,
        expectedName: String,
        expectedReturnType: String,
        register: Int,
    ) {
        assertRegistryMethod(instruction, Opcode.INVOKE_STATIC, expectedName, expectedReturnType)
        val invoke = assertIs<FiveRegisterInstruction>(instruction)
        assertEquals(1, invoke.registerCount)
        assertEquals(register, invoke.registerC)
    }

    private fun assertByteInvoke(
        instruction: Instruction,
        expectedName: String,
        expectedReturnType: String,
        register: Int,
    ) {
        assertRegistryMethod(instruction, Opcode.INVOKE_STATIC_RANGE, expectedName, expectedReturnType)
        val invoke = assertIs<RegisterRangeInstruction>(instruction)
        assertEquals(1, invoke.registerCount)
        assertEquals(register, invoke.startRegister)
    }

    private fun assertRegistryMethod(
        instruction: Instruction,
        expectedOpcode: Opcode,
        expectedName: String,
        expectedReturnType: String,
    ) {
        assertEquals(expectedOpcode, instruction.opcode)
        val reference = assertIs<MethodReference>(assertIs<ReferenceInstruction>(instruction).reference)
        assertEquals(SETTINGS_REGISTRY_DESCRIPTOR, reference.definingClass)
        assertEquals(expectedName, reference.name)
        assertEquals(listOf("Ljava/lang/String;"), reference.parameterTypes.map(CharSequence::toString))
        assertEquals(expectedReturnType, reference.returnType)
    }

    private fun assertMoveResult(
        instruction: Instruction,
        expectedOpcode: Opcode,
        expectedRegister: Int,
    ) {
        assertEquals(expectedOpcode, instruction.opcode)
        assertEquals(expectedRegister, assertIs<OneRegisterInstruction>(instruction).registerA)
    }

    private fun mutableMethod(
        registerCount: Int,
        vararg instructions: String,
    ): MutableMethod {
        val implementation = MethodImplementationBuilder(registerCount)
        instructions.forEach { implementation.addInstruction(it.toInstruction()) }
        val method =
            ImmutableMethod(
                "Lapp/crimera/test/SettingsFixture;",
                "fixture",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            )
        return MutableMethod(method)
    }

    private companion object {
        val toggle =
            ToggleSettingDefinition(
                id = "newx.test.toggle",
                titleResourceName = "piko_newx_test_toggle_title",
                summaryResourceName = null,
                order = 0,
                defaultValue = false,
            )

        val textInput =
            TextInputSettingDefinition(
                id = "newx.test.text_input",
                titleResourceName = "piko_newx_test_text_input_title",
                summaryResourceName = null,
                order = 0,
                defaultValue = "",
            )

        val multiChoice =
            MultiChoiceSettingDefinition(
                id = "newx.test.multi_choice",
                titleResourceName = "piko_newx_test_multi_choice_title",
                summaryResourceName = null,
                order = 0,
                defaultValue = emptySet(),
                options = emptyList(),
            )
    }
}
