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
import app.crimera.utils.changeString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val FOLLOW_LIST_EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/followList/FollowListHook;"

internal object BindInternalBadgeFingerprint : Fingerprint(
    strings = listOf("bindInternalBadges"),
)

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
            BindInternalBadgeFingerprint.apply {

                // This is needed in order to find the profile info parameter.
                val isStaticMethod = AccessFlags.STATIC.isSet(method.accessFlags)

                val internalBadgeStringIndex = stringMatches[0].index

                method.apply {
                    val viewType = "Landroid/view/View;"

                    val profileInfoClassType = ProfileUserInfoViewBinderFingerprint.method.parameters[1].type

                    // Identify the profile info in the method parameter, which is later passed to our custom hook.
                    var profileInfoParameter = parameters.indexOfFirst { it.type == profileInfoClassType }

                    // Identify the View parameter, which contents all the elements on profile view.
                    var viewParameter = parameters.indexOfFirst { it.type == viewType }

                    // If it is not a static function, then we need to increase the parameter count by one.
                    if (!isStaticMethod) {
                        profileInfoParameter += 1
                        viewParameter += 1
                    }

                    val internalBadgeInstructionIndex =
                        indexOfFirstInstruction(internalBadgeStringIndex, Opcode.IGET_OBJECT)
                    val internalBadgeInstructionExtraction = getInstruction(internalBadgeInstructionIndex).fieldExtractor()
                    val internalBadgeDefiningClassName = extensionToClassName(internalBadgeInstructionExtraction.definingClass)
                    val internalBadgeFieldName = internalBadgeInstructionExtraction.name
                    val internalBadgeReturnType = extensionToClassName(internalBadgeInstructionExtraction.returnType)

                    // Added instructions:
                    // Get the view  and check if its not null
                    // and then cast it to the profile model class*.
                    // class the indicator hook.
                    addInstructionsWithLabels(
                        0,
                        """
                        invoke-virtual/range {p$viewParameter .. p$viewParameter}, $viewType->getTag()Ljava/lang/Object;
                        move-result-object v1
                        if-eqz v1, :cond_piko
                        check-cast v1, $internalBadgeDefiningClassName
                        iget-object v2, v1, $internalBadgeDefiningClassName->$internalBadgeFieldName:$internalBadgeReturnType
                        move-object/from16 v0, p$profileInfoParameter
                        invoke-static {v0, v2}, ${PATCHES_DESCRIPTOR}/userprofile/FriendshipStatusIndicator;->addFriendshipIndicator(Ljava/lang/Object;Ljava/lang/Object;)V
                        """.trimIndent(),
                        ExternalLabel("cond_piko", getInstruction(0)),
                    )

                    enableSettings("followBackIndicator")
                }
            }

            // Following-list badge: same status indicator, shown per row in the account's own
            // Following list instead of on the profile page. Merged into this patch since it
            // reuses the badge-building code above; kept as its own settings toggle.
            run {
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

                // The binder also holds the FollowListData it was built with, nested inside a small
                // config object - that's where the profile whose list this is comes from, needed to
                // tell "my own Following list" apart from someone else's.
                val listConfigClass = Fingerprint(
                    returnType = "V",
                    name = "<init>",
                    parameters = listOf("Landroid/os/Bundle;", USER_SESSION_CLASS, FOLLOW_LIST_DATA_CLASS),
                ).classDef
                val listConfigField = binderClass.fields.first { it.type == listConfigClass.type }
                val followListDataField = listConfigClass.fields.first { it.type == FOLLOW_LIST_DATA_CLASS }

                val sessionField = binderClass.fields.firstOrNull { it.type == USER_SESSION_CLASS }
                    ?: throw PatchException("Expected the follow list binder to hold a UserSession")

                // FollowListData's own factory assigns its params straight to fields - the field
                // written from the first String param is the target user id.
                val followListDataFactory = mutableClassDefBy(FOLLOW_LIST_DATA_CLASS).methods.first { method ->
                    method.returnType == FOLLOW_LIST_DATA_CLASS &&
                        method.parameterTypes == listOf(listTypeEnumClass.type, "Ljava/lang/String;", "Ljava/lang/String;", "Z")
                }
                val firstStringParamRegister = followListDataFactory.implementation!!.registerCount -
                    followListDataFactory.parameterTypes.size + 1
                val targetUserIdField = followListDataFactory.instructions.firstOrNull { instruction ->
                    instruction.opcode == Opcode.IPUT_OBJECT &&
                        (instruction as TwoRegisterInstruction).registerA == firstStringParamRegister
                }?.let { (it as ReferenceInstruction).reference as FieldReference }
                    ?: throw PatchException("Expected an iput-object writing the first String parameter")

                // changeString matches by text rather than position, since R8 can reorder the
                // extension's own string constants.
                OnRowBoundExtensionFingerprint.changeString("fieldName", listTypeField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName2", listTypeValueField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName3", listConfigField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName4", followListDataField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName5", targetUserIdField.name)
                OnRowBoundExtensionFingerprint.changeString("fieldName6", sessionField.name)

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
                        "invoke-static/range {p0 .. p3}, $FOLLOW_LIST_EXTENSION_CLASS->onRowBound(Ljava/lang/Object;ILandroid/view/View;Ljava/lang/Object;)V",
                    )
                }

                enableSettings("followListNonFollowerBadge")
            }
        }
    }
