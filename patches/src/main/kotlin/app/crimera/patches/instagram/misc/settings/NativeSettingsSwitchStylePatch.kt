/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val IGDS_SWITCH = "Lcom/instagram/igds/components/switchbutton/IgdsSwitch;"
private const val STYLE_METHOD = "pikoUseMaterialSwitchStyle"
private const val PREFERENCE_STYLE =
    "Lapp/morphe/extension/instagram/settings/preference/widgets/InstagramPreferenceStyle;"

private object NativeSwitchInitializer : Fingerprint(
    definingClass = IGDS_SWITCH,
    name = "<clinit>",
)

internal val nativeSettingsSwitchStylePatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)
    execute {
        val initializer = NativeSwitchInitializer.matchAll(1..1).single().method
        val styleField = initializer.instructions.mapNotNull { instruction ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.takeIf {
                instruction.opcode == Opcode.SPUT_BOOLEAN &&
                    it.definingClass == IGDS_SWITCH && it.type == "Z"
            }
        }.singleOrNull() ?: throw PatchException("Expected one native switch style flag")
        val switchClass = mutableClassDefBy(IGDS_SWITCH)
        if (switchClass.methods.any { it.name == STYLE_METHOD }) {
            throw PatchException("Native switch style bridge already exists")
        }

        // A shortcut can load IgdsSwitch before Instagram initializes its style provider.
        val patchedMethods = mutableSetOf<String>()
        for (method in switchClass.methods) {
            val reads = method.instructions.mapIndexedNotNull { index, instruction ->
                if (instruction.opcode == Opcode.SGET_BOOLEAN &&
                    (instruction as? ReferenceInstruction)?.reference.toString() == styleField.toString()
                ) index else null
            }
            if (reads.isEmpty()) continue
            if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                throw PatchException("Native switch style read has no view instance")
            }
            for (index in reads.asReversed()) {
                val register = method.instructions[index].registersUsed.singleOrNull()
                    ?: throw PatchException("Native switch style read has no destination register")
                method.replaceInstruction(index,
                    "invoke-direct/range {p0 .. p0}, $IGDS_SWITCH->$STYLE_METHOD()Z")
                method.addInstruction(index + 1, "move-result v$register")
            }
            patchedMethods += method.name
        }
        if (!patchedMethods.containsAll(listOf("<init>", "onDraw", "setChecked"))) {
            throw PatchException("Native switch style does not cover creation, drawing and animation")
        }

        val implementation = MethodImplementationBuilder(2).apply {
            addInstruction("sget-boolean v0, $styleField".toInstruction())
            addInstruction("invoke-static {v1, v0}, $PREFERENCE_STYLE->useMaterialSwitchStyle(Landroid/view/View;Z)Z".toInstruction())
            addInstruction("move-result v0".toInstruction())
            addInstruction("return v0".toInstruction())
        }.methodImplementation
        switchClass.methods.add(MutableMethod(ImmutableMethod(
            IGDS_SWITCH, STYLE_METHOD, emptyList(), "Z", AccessFlags.PRIVATE.value,
            emptySet(), emptySet(), implementation,
        )))
    }
}
