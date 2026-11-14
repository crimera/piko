package app.crimera.patches.newx.misc.customsharingdomain

// Prevents the reported profile-share VerifyError: the saved profile must survive later boolean
// type checks, so its snapshot cannot occupy a local register reused by the original method.

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProfileNavigationUrlRegisterLifetimeTest {
    @Test
    fun `profile snapshot uses a reserved local instead of a later boolean register`() {
        val original = profileUrlGetterFixture()
        val originalRegisterCount = original.implementation!!.registerCount
        val target = original.reserveProfileSnapshotRegister()

        assertEquals(originalRegisterCount, target.snapshotRegister)
        assertTrue(target.method.implementation!!.registerCount > originalRegisterCount)

        hookProfileNavigationUrl(target.method, target.snapshotRegister)

        val instructions = target.method.instructions
        val snapshotIndex = instructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.MOVE_OBJECT &&
                (instruction as OneRegisterInstruction).registerA == target.snapshotRegister
        }
        val booleanIndex = instructions.indexOfFirst { it.opcode == Opcode.INSTANCE_OF }
        val rewriteIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>()?.name == "rewriteProfileUrl"
        }
        assertTrue(snapshotIndex in instructions.indices)
        assertTrue(booleanIndex > snapshotIndex)
        assertTrue(rewriteIndex > booleanIndex)
        assertEquals(
            target.snapshotRegister,
            instructions[rewriteIndex].registersUsed[1],
            "the profile argument must come from the reserved snapshot, not the Boolean result",
        )
        assertNotEquals(
            0,
            target.snapshotRegister,
            "v0 is overwritten by the getter's profile-variant instance-of checks",
        )
    }

    private fun profileUrlGetterFixture(): MutableMethod {
        val implementation = MethodImplementationBuilder(3)
        implementation.addInstruction(
            "iget-object p0, p0, Lcom/x/navigation/ProfileRoute;->a:Lcom/x/models/profile/Base;"
                .toInstruction(),
        )
        implementation.addInstruction(
            (
                "invoke-static {p0}, Lcom/x/navigation/ProfileNormalizer;->normalize(" +
                    "Lcom/x/models/profile/Base;)Lcom/x/models/profile/Base;"
            ).toInstruction(),
        )
        implementation.addInstruction("move-result-object p0".toInstruction())
        implementation.addInstruction(
            "instance-of v0, p0, Lcom/x/models/profile/Named;".toInstruction(),
        )
        implementation.addInstruction("const-string v1, \"https://x.com/\"".toInstruction())
        implementation.addInstruction("move-object p0, v1".toInstruction())
        implementation.addInstruction("return-object p0".toInstruction())

        return MutableMethod(
            ImmutableMethod(
                "Lcom/x/navigation/ProfileRoute;",
                "url",
                emptyList<ImmutableMethodParameter>(),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
