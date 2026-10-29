package app.crimera.patches.newx.misc.customsharingdomain

// Guards the copy/share path that builds an attribution URL directly and bypasses the shared Intent helper.

import app.crimera.patches.newx.misc.customsharingdomain.isShareAttributionUrlBuilder
import app.crimera.patches.newx.misc.customsharingdomain.rewriteShareAttributionUrlBuilder
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareAttributionUrlBuilderPatchTest {
    @Test
    fun `attribution builder result is rewritten before direct callers consume it`() {
        val method = attributionUrlBuilderFixture()
        assertTrue(method.isShareAttributionUrlBuilder())
        assertFalse(attributionUrlBuilderFixture(queryParameter = "source=").isShareAttributionUrlBuilder())

        rewriteShareAttributionUrlBuilder(method)

        val instructions = method.instructions
        val rewriteIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>()?.name == "rewriteSharedUrl"
        }
        val returnIndex = instructions.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
        assertTrue(rewriteIndex >= 0)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[rewriteIndex + 1].opcode)
        assertEquals(4, (instructions[rewriteIndex + 1] as OneRegisterInstruction).registerA)
        assertTrue(rewriteIndex < returnIndex)
    }

    private fun attributionUrlBuilderFixture(
        queryParameter: String = "s=",
    ): MutableMethod {
        val implementation = MethodImplementationBuilder(6)
        implementation.addInstruction("const-string v0, \"?\"".toInstruction())
        implementation.addInstruction("const-string v1, \"&\"".toInstruction())
        implementation.addInstruction("const-string v2, \"$queryParameter\"".toInstruction())
        implementation.addInstruction(
            "invoke-virtual {v5}, Lcom/x/share/impl/attribution/Source;->getCode()Ljava/lang/String;"
                .toInstruction(),
        )
        implementation.addInstruction("move-result-object v5".toInstruction())
        implementation.addInstruction(
            (
                "invoke-static {v4, v0, v2, v5}, Lapp/test/Strings;->concat(" +
                    "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
            ).toInstruction(),
        )
        implementation.addInstruction("move-result-object v4".toInstruction())
        implementation.addInstruction("return-object v4".toInstruction())

        return MutableMethod(
            ImmutableMethod(
                "Lapp/test/ShareAttributionUrlBuilder;",
                "appendSource",
                listOf(
                    ImmutableMethodParameter("Ljava/lang/String;", emptySet(), "url"),
                    ImmutableMethodParameter(
                        "Lcom/x/share/impl/attribution/Source;",
                        emptySet(),
                        "source",
                    ),
                ),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
