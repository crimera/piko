package app.crimera.patches.newx.misc.postdetails

// Guards the 12.29.0-alpha.04 gesture-pill regression. Container classification preserves the
// existing inset contract, while the emitted-bytecode check proves the immersive likes/repost/share
// row receives navigation padding before it renders rather than mutating the reply editor.

import app.crimera.patches.newx.settings.ToggleSettingDefinition
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
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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

    @Test
    fun `immersive action row receives navigation padding before it renders`() {
        val method = immersiveActionBarFixture()
        val actionBarCall = method.instructions.first()
        val holderProvider =
            ImmutableMethodReference(
                "Lapp/crimera/test/WindowInsetsHolder;",
                "current",
                listOf("Landroidx/compose/runtime/Composer;"),
                "Lapp/crimera/test/WindowInsetsHolder;",
            )
        val navigationBarsField =
            ImmutableFieldReference(
                "Lapp/crimera/test/WindowInsetsHolder;",
                "navigationBarsIgnoringVisibility",
                "Lapp/crimera/test/WindowInsets;",
            )
        val insetsPadding =
            ImmutableMethodReference(
                "Landroidx/compose/foundation/layout/TestLayout;",
                "windowInsetsPadding",
                listOf(
                    "Landroidx/compose/ui/Modifier;",
                    "Lapp/crimera/test/WindowInsets;",
                ),
                "Landroidx/compose/ui/Modifier;",
            )
        applyImmersiveActionBarSafeAreaHook(
            ToggleSettingDefinition(
                id = "newx.test.hide_post_reply_bar",
                titleResourceName = "piko_newx_test_title",
                summaryResourceName = null,
                order = 0,
                defaultValue = false,
            ),
            ImmersiveActionBarSafeAreaHook(
                method = method,
                actionBarCallIndex = 0,
                actionBarCall = actionBarCall,
                modifierRegister = 1,
                composerRegister = 8,
                windowInsetsHolderProvider = holderProvider,
                navigationBarsField = navigationBarsField,
                insetsPaddingCall = insetsPadding,
            ),
        )

        val instructions = method.instructions
        val actionBarCallIndex = instructions.indexOf(actionBarCall)
        val insetFieldIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<FieldReference>() == navigationBarsField
        }
        val paddingCallIndex = instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>() == insetsPadding
        }
        assertEquals(true, insetFieldIndex in 0 until actionBarCallIndex)
        assertEquals(true, paddingCallIndex in (insetFieldIndex + 1) until actionBarCallIndex)
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[paddingCallIndex + 1].opcode)
        assertEquals(
            1,
            (instructions[paddingCallIndex + 1] as OneRegisterInstruction).registerA,
            "the padded Modifier must replace the action row's Modifier register",
        )
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

    private fun immersiveActionBarFixture(): MutableMethod {
        val method = fixture(registerCount = 12)
        method.addInstructionsWithLabels(
            0,
            """
                invoke-static {v1, v8}, Lapp/crimera/test/ActionBar;->renderActionBar(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;)V
            """.trimIndent(),
        )
        return method
    }

    private fun fixture(registerCount: Int = 3): MutableMethod {
        val implementation = MethodImplementationBuilder(registerCount)
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
