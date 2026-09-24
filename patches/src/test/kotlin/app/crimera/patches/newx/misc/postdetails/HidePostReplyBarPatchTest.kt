package app.crimera.patches.newx.misc.postdetails

// Guards the failure reported against 12.29.0-alpha.04: the hide-reply-bar guards deleted the
// post-detail container's navigation-insets reservation together with the reply bar, dropping the
// fullscreen photo screen's action bar into the gesture pill. The classifier decides whether a
// container's inset is the app's own gated gesture reservation: misclassifying a GATED application
// as UNCONDITIONAL reintroduces the pill overlap, and misclassifying an UNCONDITIONAL one as GATED
// reintroduces the legacy empty inset space below the reply bar.

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HidePostReplyBarPatchTest {
    @Test
    fun `inset application classification preserves the gesture reservation contract`() {
        val gated = diamondContainer(skipArmInstruction = "const/4 v2, 0x0")
        assertEquals(
            InsetApplicationKind.GATED,
            gated.classifyInsetApplication(callIndex = 1),
            "gated insets (immersive photo screen) must keep their gesture reservation",
        )

        val unconditional = linearContainer()
        assertEquals(
            InsetApplicationKind.UNCONDITIONAL,
            unconditional.classifyInsetApplication(callIndex = 0),
            "unconditional legacy insets must stay removable with the reply bar",
        )

        assertFailsWith<PatchException> {
            diamondContainer(skipArmInstruction = "const/4 v1, 0x0")
                .classifyInsetApplication(callIndex = 1)
        }
    }

    /** `if-eqz v0, :skip` / apply arm (`invoke` + `move-result-object v1`) / `goto :merge` / skip arm / merge. */
    private fun diamondContainer(skipArmInstruction: String): MutableMethod {
        val method = fixture()
        val returnInstruction = method.instructions[0]
        method.addInstructionsWithLabels(0, skipArmInstruction)
        val skipInstruction = method.instructions[0]
        method.addInstructionsWithLabels(0, "goto :merge", ExternalLabel("merge", returnInstruction))
        method.addInstructionsWithLabels(
            0,
            """
            invoke-static {}, Lapp/crimera/test/Fixture;->insets()Landroidx/compose/ui/Modifier;
            move-result-object v1
            """.trimIndent(),
        )
        method.addInstructionsWithLabels(0, "if-eqz v0, :skip", ExternalLabel("skip", skipInstruction))
        return method
    }

    /** `invoke` + `move-result-object v1` with no gate before the call. */
    private fun linearContainer(): MutableMethod {
        val method = fixture()
        method.addInstructionsWithLabels(
            0,
            """
            invoke-static {}, Lapp/crimera/test/Fixture;->insets()Landroidx/compose/ui/Modifier;
            move-result-object v1
            """.trimIndent(),
        )
        return method
    }

    private fun fixture(): MutableMethod {
        val implementation = MethodImplementationBuilder(3)
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/HidePostReplyBarFixture;",
                "fixture",
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
