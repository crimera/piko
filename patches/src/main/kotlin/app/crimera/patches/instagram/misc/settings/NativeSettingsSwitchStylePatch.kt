/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val IGDS_SWITCH = "Lcom/instagram/igds/components/switchbutton/IgdsSwitch;"
private const val STYLE_METHOD = "pikoUseMaterialSwitchStyle"
private const val PREFERENCE_STYLE =
    "Lapp/morphe/extension/instagram/settings/preference/widgets/InstagramPreferenceStyle;"

private const val STARTING_RADIUS = 8f
private const val UNCHECKED_RADIUS = 12f
private const val CHECKED_RADIUS = 14f

/**
 * Native switch style flag, set when the switch class is loaded.
 */
private object NativeSwitchInitializer : Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "<clinit>",
    filters = listOf(fieldAccess(definingClass = "this", type = "Z", opcode = Opcode.SPUT_BOOLEAN)),
)

// A native switch with the fixed Material style.
private object MaterialSwitchConstructorFingerprint : Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "<init>",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        literal(STARTING_RADIUS),
        literal(UNCHECKED_RADIUS),
        // Thumb radius.
        fieldAccess(definingClass = "this", type = "F", opcode = Opcode.IPUT),
    ),
)

private fun materialSwitchDrawFingerprint(radiusField: FieldReference) = Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "onDraw",
    parameters = listOf("Landroid/graphics/Canvas;"),
    filters = listOf(
        fieldAccess(reference = radiusField, opcode = Opcode.IGET),
        methodCall(smali = "Landroid/graphics/drawable/Drawable;->setBounds(Landroid/graphics/Rect;)V"),
    ),
)

private fun materialSwitchSetCheckedFingerprint(radiusField: FieldReference) = Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "setChecked",
    parameters = listOf("Z"),
    filters = listOf(
        literal(STARTING_RADIUS),
        literal(UNCHECKED_RADIUS),
        literal(CHECKED_RADIUS),
        methodCall(smali = "Landroid/view/View;->startAnimation(Landroid/view/animation/Animation;)V"),
        fieldAccess(reference = radiusField, opcode = Opcode.IPUT),
    ),
)

private object MaterialSwitchSetCheckedAnimatedFingerprint : Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "setCheckedAnimated",
    parameters = listOf("Z"),
    filters = listOf(methodCall(smali = "Landroid/widget/CompoundButton;->setChecked(Z)V")),
)

private object NativeSwitchStyleReadFingerprint : Fingerprint(
    definingClass = IGDS_SWITCH,
    filters = listOf(opcode(Opcode.SGET_BOOLEAN)),
)

context(_: BytecodePatchContext)
private fun verifyFixedMaterialSwitchStyle() {
    if (NativeSwitchStyleReadFingerprint.matchOrNull() != null) {
        throw PatchException("Native switch has no verified Material style implementation")
    }
    val radiusField = MaterialSwitchConstructorFingerprint.instructionMatches.last().fieldReference
    materialSwitchDrawFingerprint(radiusField).match()
    materialSwitchSetCheckedFingerprint(radiusField).match()
    MaterialSwitchSetCheckedAnimatedFingerprint.match()
}

internal val nativeSettingsSwitchStylePatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)
    execute {
        val initializer = NativeSwitchInitializer.matchOrNull()
        if (initializer == null) {
            verifyFixedMaterialSwitchStyle()
            return@execute
        }
        val styleField = initializer.instructionMatches.first().fieldReference

        // A shortcut can load IgdsSwitch before Instagram initializes its style provider.
        val patchedMethods = mutableSetOf<String>()
        Fingerprint(
            definingClass = IGDS_SWITCH,
            filters = listOf(fieldAccess(reference = styleField, opcode = Opcode.SGET_BOOLEAN)),
        ).matchAllMethodIndicesForEach { index ->
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "invoke-direct/range {p0 .. p0}, $IGDS_SWITCH->$STYLE_METHOD()Z")
            addInstruction(index + 1, "move-result v$register")
            patchedMethods += name
        }
        if (!patchedMethods.containsAll(listOf("<init>", "onDraw", "setChecked"))) {
            throw PatchException("Native switch style does not cover creation, drawing and animation")
        }

        mutableClassDefBy(IGDS_SWITCH).methods.add(
            ImmutableMethod(
                IGDS_SWITCH,
                STYLE_METHOD,
                emptyList(),
                "Z",
                AccessFlags.PRIVATE.value,
                emptySet(),
                emptySet(),
                MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(
                    0,
                    """
                    sget-boolean v0, $styleField
                    invoke-static {p0, v0}, $PREFERENCE_STYLE->useMaterialSwitchStyle(Landroid/view/View;Z)Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )
            },
        )
    }
}
