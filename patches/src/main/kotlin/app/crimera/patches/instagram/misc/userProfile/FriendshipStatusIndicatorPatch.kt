/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FOLLOW_LIST_DATA_CLASS
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val FOLLOW_LIST_EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/followList/FollowListHook;"

internal object BindInternalBadgeFingerprint : Fingerprint(
    filters = listOf(
        string("bindInternalBadges"),
        // Internal badge view.
        fieldAccess(opcode = Opcode.IGET_OBJECT),
    ),
)

// Anchored on the null-check message that names the real FollowRowState type this method casts
// its model parameter to - unique among the classes sharing this bindView signature.
internal object FollowListBindViewFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
    strings = listOf(
        "null cannot be cast to non-null type com.instagram.user.userlist.adapter.FollowRowState",
    ),
    filters = listOf(opcode(Opcode.RETURN_VOID)),
)

internal object FollowListConfigConstructorFingerprint : Fingerprint(
    returnType = "V",
    name = "<init>",
    parameters = listOf("Landroid/os/Bundle;", USER_SESSION_CLASS, FOLLOW_LIST_DATA_CLASS),
)

/**
 * FollowListData's own factory assigns its params straight to fields.
 */
internal object FollowListDataFactoryFingerprint : Fingerprint(
    definingClass = FOLLOW_LIST_DATA_CLASS,
    returnType = FOLLOW_LIST_DATA_CLASS,
    parameters = listOf("L", "Ljava/lang/String;", "Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Object;-><init>()V"),
        // List type.
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
        // Target user id, the first String param.
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object OnRowBoundExtensionFingerprint : Fingerprint(
    definingClass = FOLLOW_LIST_EXTENSION_CLASS,
    name = "onRowBound",
)

@Suppress("unused")
val friendshipStatusIndicatorPatch =
    bytecodePatch(
        name = "Friendship status indicator",
        description =
            "Adds a follows you back status label on the profile page and" +
                "shows a detailed friendship status breakdown on click",
    ) {

        dependsOn(settingsPatch, userDataEntity, profileInfoEntity, instagramDialogBoxEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // This constant stores the value of the obfuscated profile info class,
            // which is later used to find the index of the parameter.
            var profileInfoClassName: String

            // This fingerprint is used to identify the internal badge, which is used for displaying follow back status.
            BindInternalBadgeFingerprint.let {
                val viewType = "Landroid/view/View;"
                val parameterTypes = it.originalMethod.parameterTypes.map(CharSequence::toString)
                // Parameter registers, offset by one when the method is not static.
                val thisOffset = if (AccessFlags.STATIC.isSet(it.originalMethod.accessFlags)) 0 else 1

                // Identify the profile info in the method parameter, which is later passed to our custom hook.
                val profileInfoClassType = ProfileUserInfoViewBinderFingerprint.originalMethod.parameterTypes[1].toString()
                val profileInfoParameter = parameterTypes.indexOf(profileInfoClassType) + thisOffset

                // Identify the View parameter, which contents all the elements on profile view.
                val viewParameter = parameterTypes.indexOf(viewType) + thisOffset

                val internalBadgeField = it.instructionMatches.last().fieldReference

                // Added instructions:
                // Get the view  and check if its not null
                // and then cast it to the profile model class*.
                // class the indicator hook.
                it.method.addInstructionsWithLabels(
                    0,
                    """
                    invoke-virtual/range {p$viewParameter .. p$viewParameter}, $viewType->getTag()Ljava/lang/Object;
                    move-result-object v1
                    if-eqz v1, :cond_piko
                    check-cast v1, ${internalBadgeField.definingClass}
                    iget-object v2, v1, $internalBadgeField
                    move-object/from16 v0, p$profileInfoParameter
                    invoke-static {v0, v2}, ${PATCHES_DESCRIPTOR}/userprofile/FriendshipStatusIndicator;->addFriendshipIndicator(Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                    ExternalLabel("cond_piko", it.method.getInstruction(0)),
                )

                enableSettings("followBackIndicator")
            }

            // Following-list badge: same status indicator, shown per row in the account's own
            // Following list instead of on the profile page. Merged into this patch since it
            // reuses the badge-building code above; kept as its own settings toggle.
            run {
                // One binder instance per list type, with the type baked in as a final field.
                val binderClass = FollowListBindViewFingerprint.originalClassDef
                // The list type is the enum the FollowListData factory takes.
                val listTypeEnumClass = classDefBy(FollowListDataFactoryFingerprint.originalMethod.parameterTypes.first().toString())
                val listTypeField = binderClass.fields.first { it.type == listTypeEnumClass.type }
                // The enum's only non-static String field holds its raw value (e.g. "self_following").
                val listTypeValueField = listTypeEnumClass.fields.first { field ->
                    field.type == "Ljava/lang/String;" && !AccessFlags.STATIC.isSet(field.accessFlags)
                }

                // The binder also holds the FollowListData it was built with, nested inside a small
                // config object - that's where the profile whose list this is comes from, needed to
                // tell "my own Following list" apart from someone else's.
                val listConfigClass = FollowListConfigConstructorFingerprint.originalClassDef
                val listConfigField = binderClass.fields.first { it.type == listConfigClass.type }
                val followListDataField = listConfigClass.fields.first { it.type == FOLLOW_LIST_DATA_CLASS }

                val sessionField = binderClass.fields.first { it.type == USER_SESSION_CLASS }

                val targetUserIdField = FollowListDataFactoryFingerprint.instructionMatches.last().fieldReference

                // changeString matches by text rather than position, since R8 can reorder the
                // extension's own string constants.
                OnRowBoundExtensionFingerprint.changeString("fieldName", listTypeField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName2", listTypeValueField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName3", listConfigField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName4", followListDataField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName5", targetUserIdField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName6", sessionField.name)

                // bindView's "model" parameter is already the row's User.
                FollowListBindViewFingerprint.let {
                    // /range is required since p0..p3 fall outside the compact invoke encoding's
                    // register range here.
                    it.method.addInstructionsAtControlFlowLabel(
                        it.instructionMatches.first().index,
                        "invoke-static/range {p0 .. p3}, $FOLLOW_LIST_EXTENSION_CLASS->onRowBound(Ljava/lang/Object;ILandroid/view/View;Ljava/lang/Object;)V",
                    )
                }

                enableSettings("followListNonFollowerBadge")
            }
        }
    }
