/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.util.findInstructionIndicesReversed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val MATERIAL_YOU_BUTTONS = "Lapp/morphe/extension/instagram/theme/MaterialYouButtons;"

/** The painter of SECONDARY IgdsButtons (X.0DhX in 452): its elevation toggle reads this attribute. */
private object SecondaryButtonElevationFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/widget/TextView;", "Z"),
    filters = listOf(
        resourceLiteral(ResourceType.ATTR, "igds_color_secondary_button_elevated_panavision"),
    ),
)

/** The same painter's setup (A04 in 452): fills its Paint from ?igds_color_secondary_button_panavision. */
private object SecondaryButtonPainterFingerprint : Fingerprint(
    classFingerprint = SecondaryButtonElevationFingerprint,
    returnType = "V",
    parameters = listOf("Landroid/content/res/Resources;", "Landroid/widget/TextView;"),
)

/** X.05iJ.A00(String, String) in 452: builds a RingSpecImpl. Its class also resolves the ring colours. */
private object StoryRingSpecFingerprint : Fingerprint(
    returnType = "Lcom/instagram/api/schemas/RingSpecImpl;",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
)

/** NoteBubbleView.A0E(Context) in 452: the bubble fill colour. */
private object NoteBubbleColorFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/direct/inbox/notes/ui/NoteBubbleView;",
    returnType = "I",
    parameters = listOf("Landroid/content/Context;"),
)

/** Secondary buttons (Edit profile, Share profile...) get the container tone. */
internal fun installSecondaryButtonTint() {
    SecondaryButtonPainterFingerprint.method.apply {
        findInstructionIndicesReversed { opcode == Opcode.RETURN_VOID }.forEach { index ->
            addInstruction(
                index,
                "invoke-static/range {p0 .. p2}, $MATERIAL_YOU_BUTTONS->onSecondaryPainter" +
                    "(Ljava/lang/Object;Landroid/content/res/Resources;Landroid/widget/TextView;)V",
            )
        }
    }
}

/** The default story ring gradient and note bubble fills. */
internal fun installStoryRingAndNoteHooks() {
    // The ring colours are hex strings resolved once by a static (attr, fallback) -> String method.
    val resolver = mutableClassDefBy(StoryRingSpecFingerprint.classDef).methods.single {
        AccessFlags.STATIC.isSet(it.accessFlags) &&
            it.returnType == "Ljava/lang/String;" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("I", "I")
    }
    resolver.findInstructionIndicesReversed { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
        val register = (resolver.instructions.elementAt(index) as OneRegisterInstruction).registerA
        resolver.addInstructions(
            index,
            """
            invoke-static {p0, p1, v$register}, $MATERIAL_YOU_BUTTONS->ringColor(IILjava/lang/String;)Ljava/lang/String;
            move-result-object v$register
            """.trimIndent(),
        )
    }

    NoteBubbleColorFingerprint.method.apply {
        findInstructionIndicesReversed { opcode == Opcode.RETURN }.forEach { index ->
            val register = (instructions.elementAt(index) as OneRegisterInstruction).registerA
            addInstructions(
                index,
                """
                invoke-static {p1, v$register}, $MATERIAL_YOU_BUTTONS->noteBubbleColor(Landroid/content/Context;I)I
                move-result v$register
                """.trimIndent(),
            )
        }
    }
}
