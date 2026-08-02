/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val COMPOUND_BUTTON_LISTENER_DESCRIPTOR =
    "Landroid/widget/CompoundButton\$OnCheckedChangeListener;"
private const val MATERIAL_YOU_LEGACY_HELPER_NAME = "pikoAddMaterialYouSwitch"

private data class LegacySwitchItemBinding(
    val constructor: MethodReference,
    val descriptionField: FieldReference,
)

context(patchContext: BytecodePatchContext)
internal fun installLegacyMaterialYouToggle() {
    val constructor =
        LegacyDarkModeFragmentConstructorFingerprint
            .matchAll(1..1)
            .single()
            .method
    val owner = constructor.definingClass
    val ownerClass = patchContext.mutableClassDefBy(owner)
    val legacyBindings =
        ownerClass.methods.mapNotNull { method ->
            if (
                method.name != "onResume" ||
                method.parameterTypes.isNotEmpty() ||
                method.returnType != "V" ||
                AccessFlags.STATIC.isSet(method.accessFlags)
            ) {
                null
            } else {
                findLegacyBinding(method)
            }
        }
    if (legacyBindings.size != 1) {
        throw PatchException(
            "Expected one legacy theme onResume RadioGroup/adapter binding in $owner, " +
                "found ${legacyBindings.size}",
        )
    }
    if (ownerClass.methods.any { it.name == MATERIAL_YOU_LEGACY_HELPER_NAME }) {
        throw PatchException(
            "Material You legacy helper already exists in $owner",
        )
    }

    val switchBinding = deriveSwitchItemBinding()
    val helper =
        createLegacyMaterialYouHelper(
            owner = owner,
            switchBinding = switchBinding,
        )
    if (!ownerClass.methods.add(helper)) {
        throw PatchException(
            "Failed to add legacy helper $owner->$MATERIAL_YOU_LEGACY_HELPER_NAME",
        )
    }

    val binding = legacyBindings.single()
    if (binding.collectionRegister !in 0..0xf) {
        throw PatchException(
            "Legacy Material You collection register requires a 4-bit register, " +
                "found v${binding.collectionRegister}",
        )
    }
    binding.method.addInstruction(
        binding.bindIndex,
        """
        invoke-static {v${binding.collectionRegister}}, $owner->$MATERIAL_YOU_LEGACY_HELPER_NAME(Ljava/util/Collection;)V
        """.trimIndent(),
    )
}

context(patchContext: BytecodePatchContext)
private fun deriveSwitchItemBinding(): LegacySwitchItemBinding {
    val constructor =
        SwitchItemConstructorFingerprint
            .matchAll(1..1)
            .single()
            .method
    val switchType = constructor.definingClass
    if (
        constructor.name != "<init>" ||
        constructor.returnType != "V" ||
        constructor.parameterTypes.map(CharSequence::toString) !=
        listOf(
            COMPOUND_BUTTON_LISTENER_DESCRIPTOR,
            "Ljava/lang/CharSequence;",
            "Z",
        ) ||
        !switchType.isObjectDescriptor()
    ) {
        throw PatchException(
            "Invalid derived SwitchItem listener/title/checked constructor: " +
                "$switchType->${constructor.name}(${constructor.parameterTypes.joinToString()})" +
                constructor.returnType,
        )
    }

    val switchClass = patchContext.mutableClassDefBy(switchType)
    if (switchClass.type != switchType) {
        throw PatchException(
            "Derived SwitchItem constructor owner mismatch: " +
                "${switchClass.type} != $switchType",
        )
    }
    val charSequenceFields =
        switchClass.fields.filter { it.type == "Ljava/lang/CharSequence;" }
    if (charSequenceFields.size != 2) {
        throw PatchException(
            "Expected exactly two SwitchItem CharSequence fields, " +
                "found ${charSequenceFields.size}",
        )
    }

    val constructorCharSequenceFields =
        constructor.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT) {
                return@mapNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapNotNull null
            reference.takeIf {
                it.definingClass == switchType &&
                    it.type == "Ljava/lang/CharSequence;"
            }
        }.distinctBy { Triple(it.definingClass, it.name, it.type) }
    if (constructorCharSequenceFields.size != 1) {
        throw PatchException(
            "Expected the derived SwitchItem constructor to assign one CharSequence field, " +
                "found ${constructorCharSequenceFields.size}",
        )
    }
    val titleField = constructorCharSequenceFields.single()
    val descriptionFields =
        charSequenceFields.filterNot {
            it.definingClass == titleField.definingClass &&
                it.name == titleField.name &&
                it.type == titleField.type
        }
    if (descriptionFields.size != 1) {
        throw PatchException(
            "Expected one derived SwitchItem description CharSequence field, " +
                "found ${descriptionFields.size}",
        )
    }

    return LegacySwitchItemBinding(
        constructor = constructor,
        descriptionField = descriptionFields.single(),
    )
}

private fun createLegacyMaterialYouHelper(
    owner: String,
    switchBinding: LegacySwitchItemBinding,
): MutableMethod {
    val switchConstructor = switchBinding.constructor
    val switchType = switchConstructor.definingClass
    val implementationBuilder = MethodImplementationBuilder(6)
    implementationBuilder.addInstruction(
        """
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isAvailable()Z
        """.trimIndent().toInstruction(),
    )
    implementationBuilder.addInstruction("move-result v0".toInstruction())
    implementationBuilder.addInstruction(
        BuilderInstruction21t(
            Opcode.IF_EQZ,
            0,
            implementationBuilder.getLabel("piko_material_you_unavailable"),
        ),
    )
    """
    invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getLegacyToggleListener()Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;
    move-result-object v1
    invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getMaterialYouTitle()Ljava/lang/String;
    move-result-object v2
    invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isEnabled()Z
    move-result v0
    new-instance v4, $switchType
    invoke-direct {v4, v1, v2, v0}, $switchConstructor
    invoke-interface {v5, v4}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z
    """.trimIndent().lineSequence().forEach { instruction ->
        implementationBuilder.addInstruction(instruction.toInstruction())
    }
    implementationBuilder.addLabel("piko_material_you_unavailable")
    implementationBuilder.addInstruction("return-void".toInstruction())

    val accessFlags =
        AccessFlags.PRIVATE.value or
            AccessFlags.STATIC.value or
            AccessFlags.FINAL.value
    val helper =
        MutableMethod(
            ImmutableMethod(
                owner,
                MATERIAL_YOU_LEGACY_HELPER_NAME,
                listOf(
                    ImmutableMethodParameter(COLLECTION_DESCRIPTOR, emptySet(), null),
                ),
                "V",
                accessFlags,
                emptySet(),
                emptySet(),
                implementationBuilder.methodImplementation,
            ),
        )
    if (
        helper.definingClass != owner ||
        helper.name != MATERIAL_YOU_LEGACY_HELPER_NAME ||
        helper.parameterTypes.map(CharSequence::toString) != listOf(COLLECTION_DESCRIPTOR) ||
        helper.returnType != "V" ||
        !AccessFlags.PRIVATE.isSet(helper.accessFlags) ||
        !AccessFlags.STATIC.isSet(helper.accessFlags) ||
        helper.implementation?.registerCount != 6 ||
        firstParameterRegister(helper) != 5 ||
        helper.instructions.count { it.opcode == Opcode.IF_EQZ } != 1 ||
        helper.instructions.any { instruction ->
            instruction.registersUsed.any { it !in 0..0xf }
        }
    ) {
        throw PatchException("Invalid legacy Material You SwitchItem helper")
    }

    return helper
}
