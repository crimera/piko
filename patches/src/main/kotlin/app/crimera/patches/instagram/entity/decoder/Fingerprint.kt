/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.utils.Constants.EDIT_MEDIA_INFO_FRAGMENT_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

object EditMediaInfoGetCurrentMediaIdFingerprint : Fingerprint(
    definingClass = EDIT_MEDIA_INFO_FRAGMENT_CLASS,
    returnType = "Ljava/lang/String;",
    custom = { method, _ -> method.currentMediaIndexFieldOrNull() != null },
)

internal fun Method.currentMediaIndexFieldOrNull(): FieldReference? {
    if (definingClass != EDIT_MEDIA_INFO_FRAGMENT_CLASS || returnType != "Ljava/lang/String;") return null
    val expectedParameters = if (AccessFlags.STATIC.isSet(accessFlags)) listOf(definingClass) else emptyList()
    if (parameterTypes.map { it.toString() } != expectedParameters) return null

    val instructions = implementation?.instructions?.take(10) ?: return null
    if (instructions.map { it.opcode } != listOf(
            Opcode.IGET_OBJECT, Opcode.IF_EQZ, Opcode.IGET, Opcode.CONST_4, Opcode.IF_LTZ,
            Opcode.IGET_OBJECT, Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT, Opcode.IF_GE, Opcode.INVOKE_STATIC,
        )
    ) return null

    val stateField = instructions[0].getReference<FieldReference>() ?: return null
    val indexField = instructions[2].getReference<FieldReference>() ?: return null
    val listField = instructions[5].getReference<FieldReference>() ?: return null
    val sizeMethod = instructions[6].getReference<MethodReference>() ?: return null
    val mediaGetter = instructions[9].getReference<MethodReference>() ?: return null
    if (stateField.definingClass != definingClass || indexField.definingClass != stateField.type || indexField.type != "I" ||
        listField.definingClass != definingClass || listField.type != "Ljava/util/List;" ||
        sizeMethod.definingClass != "Ljava/util/List;" || sizeMethod.name != "size" ||
        sizeMethod.parameterTypes.isNotEmpty() || sizeMethod.returnType != "I" ||
        mediaGetter.parameterTypes.map { it.toString() } != listOf("Ljava/util/List;", "I") ||
        mediaGetter.returnType != MEDIA_CLASS_NAME
    ) return null

    val stateRegisters = instructions[0].registersUsed
    val indexRegisters = instructions[2].registersUsed
    val listRegisters = instructions[5].registersUsed
    val sizeRegisters = instructions[7].registersUsed
    if (stateRegisters.size != 2 || indexRegisters.size != 2 || listRegisters.size != 2 || sizeRegisters.size != 1 ||
        indexRegisters[1] != stateRegisters[0] || listRegisters[1] != stateRegisters[1] ||
        instructions[1].registersUsed != listOf(stateRegisters[0]) ||
        instructions[4].registersUsed != listOf(indexRegisters[0]) ||
        instructions[6].registersUsed != listOf(listRegisters[0]) ||
        instructions[8].registersUsed != listOf(indexRegisters[0], sizeRegisters[0]) ||
        instructions[9].registersUsed != listOf(listRegisters[0], indexRegisters[0])
    ) return null

    return indexField
}

object CommentButtonOnClickFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap", "comment_share_click"),
)

internal object UserTagInfoDictInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/schemas/UserTagInfoDict;",
    name = "<init>",
)

object ReelsInlineQualitySurveyRelatedFingerprint : Fingerprint(
    strings = listOf("reels_inline_quality_survey"),
    parameters = listOf(MEDIA_CLASS_NAME),
)
