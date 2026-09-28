/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.followList

import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/followList/FollowListHook;"

// Anchored on the null-check message that names the real FollowRowState type this method casts
// its model parameter to - unique among the classes sharing this bindView signature.
internal object FollowListBindViewFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
    strings = listOf(
        "null cannot be cast to non-null type com.instagram.user.userlist.adapter.FollowRowState",
    ),
)

internal object OnRowBoundExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "onRowBound",
)

@Suppress("unused")
val followListRowBadgePatch =
    bytecodePatch(
        name = "Show non-followers in Following list",
        description = "Adds a badge to Following-list rows for accounts that don't follow you back. Experimental.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(userDataEntity)

        execute {
            // One binder instance per list type, with the type baked in as a final field.
            val binderClass = FollowListBindViewFingerprint.classDef
            val listTypeField = binderClass.fields.firstOrNull { field ->
                classDefByOrNull(field.type)?.superclass == "Ljava/lang/Enum;"
            } ?: throw PatchException("Expected the follow list binder to have an enum-typed list type field")

            val listTypeEnumClass = classDefBy(listTypeField.type)
            // The enum's only non-static String field holds its raw value (e.g. "self_following").
            val listTypeValueField = listTypeEnumClass.fields.firstOrNull { field ->
                field.type == "Ljava/lang/String;" && !AccessFlags.STATIC.isSet(field.accessFlags)
            } ?: throw PatchException("Expected the follow list type enum to have a String value field")

            // changeString matches by text rather than position, since R8 can reorder the
            // extension's own string constants.
            OnRowBoundExtensionFingerprint.changeString("fieldName", listTypeField.name)
            OnRowBoundExtensionFingerprint.changeString("fieldName2", listTypeValueField.name)

            // bindView's "model" parameter is already the row's User.
            FollowListBindViewFingerprint.method.apply {
                val returnInstruction = instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_VOID }
                    .singleOrNull()
                    ?: throw PatchException("Expected exactly one return instruction in bindView")

                // /range is required since p0..p3 fall outside the compact invoke encoding's
                // register range here.
                addInstructionsAtControlFlowLabel(
                    returnInstruction.index,
                    "invoke-static/range {p0 .. p3}, $EXTENSION_CLASS->onRowBound(Ljava/lang/Object;ILandroid/view/View;Ljava/lang/Object;)V",
                )
            }

            enableSettings("followListNonFollowerBadge")
        }
    }
