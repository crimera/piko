/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline.explore

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomizeExploreTabsHookTest {
    private val urpComponentType = "Lcom/x/urp/FixtureComponent;"
    private val urpTimelineType = "Lcom/x/repositories/urp/FixtureTimeline;"
    private val pagesType = "Lcom/arkivanov/decompose/router/pages/FixturePages;"
    private val segmentedField =
        ImmutableFieldReference(urpTimelineType, "c", "Ljava/lang/String;")

    @Test
    fun `explore repository registration relocates incoming branches onto the hook`() {
        val method = exploreCtorFixture()
        val factoryCall = method.instructions[1]
        method.addInstructionsWithLabels(
            0,
            """
                const/4 v0, 0x0
                if-eqz v0, :factory
            """.trimIndent(),
            ExternalLabel("factory", factoryCall),
        )
        val factoryIndex = method.instructions.indexOf(factoryCall)

        injectExploreRepositoryRegistration(
            method = method,
            urpFactoryCallIndex = factoryIndex,
            repoArgRegister = 4,
        )

        val branch = method.instructions[1] as BuilderOffsetInstruction
        val registerCall = method.instructions[branch.target.location.index]
        assertEquals(Opcode.INVOKE_STATIC, registerCall.opcode)
        assertEquals("registerExploreRepository", registerCall.getReference<MethodReference>()?.name)
        assertEquals(listOf(4), registerCall.registersUsed)
    }

    @Test
    fun `pages update hooks filter list sync search list and clamp selected index`() {
        val method = pagesUpdateFixture()

        val hooks =
            analyzeUrpPagesUpdateHooks(
                instructions = method.instructions,
                urpComponentType = urpComponentType,
                segmentedTimelineIdField = segmentedField,
                pagesType = pagesType,
                ownerDescription = method.toString(),
            )
        assertEquals(8, hooks.componentRegister)
        assertEquals(2, hooks.listRegister)
        assertNotNull(hooks.searchListCastIndex)
        assertEquals(0, hooks.searchListRegister)
        assertEquals(4, hooks.selectedIndexRegister)
        assertEquals(2, hooks.itemsRegister)

        injectUrpPagesFilterHooks(method, hooks)

        val insns = method.instructions
        val filterCallIndex =
            insns.indexOfFirst { insn ->
                insn.opcode == Opcode.INVOKE_STATIC &&
                    insn.getReference<MethodReference>()?.name == "filterForComponent"
            }
        assertEquals(listOf(8, 2), insns[filterCallIndex].registersUsed)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, insns[filterCallIndex + 1].opcode)
        assertEquals(2, (insns[filterCallIndex + 1] as OneRegisterInstruction).registerA)

        val searchSyncIndex =
            insns.indexOfFirst { insn ->
                insn.opcode == Opcode.MOVE_OBJECT &&
                    (insn as TwoRegisterInstruction).registerA == 0 &&
                    insn.registerB == 2
            }
        assert(searchSyncIndex > filterCallIndex)

        val clampCallIndex =
            insns.indexOfFirst { insn ->
                insn.opcode == Opcode.INVOKE_STATIC &&
                    insn.getReference<MethodReference>()?.name == "clampSelectedIndex"
            }
        assertEquals(listOf(4, 2), insns[clampCallIndex].registersUsed)
        assertEquals(Opcode.MOVE_RESULT, insns[clampCallIndex + 1].opcode)
        assertEquals(4, (insns[clampCallIndex + 1] as OneRegisterInstruction).registerA)
        assertEquals(Opcode.NEW_INSTANCE, insns[clampCallIndex + 2].opcode)
    }

    private fun exploreCtorFixture(): MutableMethod {
        val impl =
            MethodImplementationBuilder(8).apply {
                addInstruction("const/4 v4, 0x0".toInstruction())
                addInstruction(
                    "invoke-static {v4}, Lcom/x/urp/Factory;->a(Lcom/x/repositories/urp/c;)Lcom/x/urp/FixtureComponent;".toInstruction(),
                )
                addInstruction("return-void".toInstruction())
            }.methodImplementation
        return MutableMethod(
            ImmutableMethod(
                "Lcom/x/explore/FixtureExplore;",
                "<init>",
                emptyList(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
                emptySet(),
                emptySet(),
                impl,
            ),
        )
    }

    private fun pagesUpdateFixture(): MutableMethod {
        val impl =
            MethodImplementationBuilder(12).apply {
                addInstruction("check-cast v8, $urpComponentType".toInstruction())
                addInstruction("iget-object v2, v0, Lcom/x/result/c;->a:Ljava/lang/Object;".toInstruction())
                addInstruction("check-cast v2, Ljava/util/List;".toInstruction())
                addInstruction("iget-object v0, v0, Lcom/x/result/c;->a:Ljava/lang/Object;".toInstruction())
                addInstruction("check-cast v0, Ljava/util/List;".toInstruction())
                addInstruction("iget-object v5, v5, $urpTimelineType->c:Ljava/lang/String;".toInstruction())
                addInstruction("const/4 v4, 0x0".toInstruction())
                addInstruction("new-instance v0, $pagesType".toInstruction())
                addInstruction("invoke-direct {v0, v4, v2}, $pagesType-><init>(ILjava/util/List;)V".toInstruction())
                addInstruction("return-object v0".toInstruction())
            }.methodImplementation
        return MutableMethod(
            ImmutableMethod(
                "Lcom/x/urp/FixturePagesLambda;",
                "invoke",
                listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)),
                "Ljava/lang/Object;",
                AccessFlags.PUBLIC.value,
                emptySet(),
                emptySet(),
                impl,
            ),
        )
    }
}
