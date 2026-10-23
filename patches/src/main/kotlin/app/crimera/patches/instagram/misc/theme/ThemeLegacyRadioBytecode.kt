/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.common.parameterRegisterStart
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

context(patchContext: BytecodePatchContext)
internal fun installLegacyNativeThemeModeSync() {
    val delegatedMatch = LegacyDarkModeOnResumeFingerprint.matchOrNull(
        LegacyDarkModeFragmentConstructorFingerprint.originalClassDef,
    )
    val match = delegatedMatch ?: InlineLegacyDarkModeOnResumeFingerprint.matchSingle()
    val method = match.method
    val idField = match.instructionMatches[0].fieldReference
    val selectedIdIndex = match.instructionMatches[0].index
    val factoryCall = match.instructionMatches[1]
    val arguments = factoryCall.instruction.registersUsed
    val rowNewIndex = if (delegatedMatch != null) factoryCall.index else factoryCall.index - 1
    val (listenerRegister, selectedIdRegister) =
        if (delegatedMatch != null) arguments else arguments.drop(1)

    val items = legacyRadioItemsFingerprint(idField.definingClass).match().instructionMatches
    fun idFor(index: Int, label: String) =
        items[index].instruction.getReference<StringReference>()!!.string.toIntOrNull()
            ?: throw PatchException("Native $label RadioItem has no numeric id")
    val packedIds =
        packLegacyNativeThemeIds(
            lightId = idFor(2, "Light"),
            darkId = idFor(0, "Dark"),
            systemId = idFor(4, "System-default"),
        )

    val rowRegister = if (delegatedMatch != null) {
        inlineLegacyRadioRowFactory(method, factoryCall)
    } else {
        val allocation = method.getInstruction(rowNewIndex)
        if (allocation.opcode != Opcode.NEW_INSTANCE ||
            allocation.registersUsed != listOf(arguments.first()) ||
            allocation.getReference<TypeReference>()?.type != factoryCall.methodReference.definingClass
        ) throw PatchException("Expected the inline theme RadioGroup allocation before its constructor")
        arguments.first()
    }
    val packedIdsRegister =
        method.findFreeRegister(
            rowNewIndex,
            rowRegister,
            listenerRegister,
            selectedIdRegister,
        )
    val selectionTempRegister = method.findFreeRegister(selectedIdIndex + 1, selectedIdRegister)
    val injectedRegisters =
        listOf(
            listenerRegister,
            packedIdsRegister,
            selectedIdRegister,
            selectionTempRegister,
        )
    if (
        injectedRegisters.any { it !in 0..0xf || it >= method.p0Register } ||
        selectionTempRegister == selectedIdRegister
    ) {
        throw PatchException("Legacy theme RadioGroup sync requires local 4-bit registers")
    }

    method.addInstructions(
        rowNewIndex,
        """
        const v$packedIdsRegister, $packedIds
        ${legacyNativeThemeListenerInvocation(listenerRegister, packedIdsRegister)}
        move-result-object v$listenerRegister
        """.trimIndent(),
    )
    method.addInstructions(
        selectedIdIndex + 1,
        """
        const v$selectionTempRegister, $packedIds
        invoke-static {v$selectedIdRegister, v$selectionTempRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getLegacySelectedRadioId(Ljava/lang/String;I)Ljava/lang/String;
        move-result-object v$selectedIdRegister
        """.trimIndent(),
    )
}

/**
 * Inlines the delegated RadioGroup row factory, so the row constructor and list add are in the method itself.
 *
 * @return the row register.
 */
context(patchContext: BytecodePatchContext)
private fun inlineLegacyRadioRowFactory(
    method: MutableMethod,
    factoryCall: Match.InstructionMatch,
): Int {
    val helper = legacyRadioRowFactoryFingerprint(factoryCall.methodReference).match()
    val constructor = helper.instructionMatches[0].methodReference
    val add = helper.instructionMatches[1].methodReference
    val rowClass = patchContext.classDefBy(constructor.definingClass)
    if (!AccessFlags.PUBLIC.isSet(rowClass.accessFlags) || rowClass.methods.none {
        it.toString() == constructor.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags)
    }) throw PatchException("Legacy RadioGroup constructor is not publicly accessible")

    val index = factoryCall.index
    val args = factoryCall.instruction.registersUsed
    val scratch = method.findFreeRegister(index, *args.toIntArray())
    if ((args + scratch).any { it !in 0..15 } || scratch >= method.p0Register) {
        throw PatchException("Legacy RadioGroup factory requires local 4-bit registers")
    }
    method.replaceInstruction(index, "new-instance v$scratch, ${constructor.definingClass}")
    method.addInstructions(index + 1, """
        invoke-direct {v$scratch, v${args[0]}, v${args[1]}, v${args[3]}}, $constructor
        invoke-virtual {v${args[2]}, v$scratch}, $add
    """.trimIndent())
    return scratch
}

internal fun packLegacyNativeThemeIds(
    lightId: Int,
    darkId: Int,
    systemId: Int,
): Int {
    val ids = listOf(lightId, darkId, systemId)
    if (ids.any { it !in 0..0xff }) {
        throw PatchException("Legacy theme radio IDs must fit in one unsigned byte")
    }
    return lightId or (darkId shl 8) or (systemId shl 16)
}
