package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object BindInternalBadgeFingerprint : Fingerprint(
    strings = listOf("bindInternalBadges")
)

internal object BindRowViewTypesFingerprint :Fingerprint (
    strings = listOf("NONE should not map to item type")
)

internal object GetViewingProfileUserObjectExtensionFingerprint: Fingerprint(
    custom = { method, classDef ->
        method.name == "getViewingProfileUserObject" && classDef.type.endsWith("userprofile/FriendshipStatusIndicator;")
    }
)


@Suppress("unused")
val followBackIndicatorPatch = bytecodePatch(
    name = "Follow back indicator",
    description = "Adds a label on the profile page, indicating whether a user is follows you back.",
) {

    dependsOn(settingsPatch, userFriendshipStatusEntity)

    compatibleWith("com.instagram.android")

    execute {
        // This constant stores the value of the obfuscated profile info class,
        // which is later used to find the index of the parameter.
        var profileInfoClassName:String

        // This fingerprint is used to identify field name in obfuscated profile info class,
        // that holds user data.
        BindRowViewTypesFingerprint.method.apply {
            val igetObjectInstruction = instructions.first { it.opcode == Opcode.IGET_OBJECT  }
            val fieldExtractions = igetObjectInstruction.fieldExtractor()
            val userObjectFieldName = fieldExtractions.name
            GetViewingProfileUserObjectExtensionFingerprint.changeFirstString(userObjectFieldName)
            profileInfoClassName = fieldExtractions.definingClass
        }
        // This fingerprint is used to identify the internal badge, which is used for displaying follow back status.
        BindInternalBadgeFingerprint.method.apply {
            val internalBadgeStringIndex = BindInternalBadgeFingerprint.stringMatches[0].index

            // Identify the profile info in the method parameter, which is later passed to our custom hook.
            val profileInfoParameter = parameters.indexOfFirst { classNameToExtension(it.type) == profileInfoClassName }

            val internalBadgeInstructionIndex = indexOfFirstInstruction(internalBadgeStringIndex, Opcode.IGET_OBJECT)
            val internalBadgeInstruction = getInstruction(internalBadgeInstructionIndex)
            // Internal badge is an element/view, which is used internally to mark developers.
            // We hook and update its text to display the follow back status.
            val internalBadgeRegistries = internalBadgeInstruction.registersUsed
            val internalBadgeRegistry = internalBadgeRegistries[0]
            // User profile page (obfuscated) contains all the elements that are present on the user page.
            // We are hooking it in order to find user session, which is used to get info on logged in user.
            val userProfilePageRegistry = internalBadgeRegistries[1]

            // Finding the necessary dummy registries.
            val dummyRegistryInstructionIndex = indexOfFirstInstruction(internalBadgeInstructionIndex + 1, Opcode.IGET_OBJECT)
            val dummyRegistry1 = getInstruction(dummyRegistryInstructionIndex).registersUsed[0]
            val dummyRegistry2 = getInstruction(internalBadgeStringIndex).registersUsed[0]

            // Instruction to which the call needs to transfer after our hook.
            val invokeStaticRangeIndex = indexOfFirstInstruction(internalBadgeInstructionIndex, Opcode.INVOKE_STATIC_RANGE)

            val userSessionClassName = "Lcom/instagram/common/session/UserSession;"
            // Finding the user profile page (obfuscated) class name.
            val userProfilePageElementsClassName = internalBadgeInstruction.getReference<FieldReference>()!!.definingClass
            // Finding the user session field.
            val userSessionFieldName =
                mutableClassDefBy(userProfilePageElementsClassName)
                    .fields
                    .first { it.type == userSessionClassName }.name

            // Added instructions:
            // Get the user session.
            // Move the profile info parameter to a suitable registry.
            // Call our hook, which will update the badge.
            addInstructionsWithLabels(
                internalBadgeInstructionIndex + 1,
                """
                    iget-object v$dummyRegistry1, v$userProfilePageRegistry, $userProfilePageElementsClassName->$userSessionFieldName:$userSessionClassName
                    move-object/from16 v$dummyRegistry2, p$profileInfoParameter
                    
                    invoke-static {v$dummyRegistry1,v$dummyRegistry2, v$internalBadgeRegistry}, ${PATCHES_DESCRIPTOR}/userprofile/FriendshipStatusIndicator;->indicators($userSessionClassName Ljava/lang/Object;Ljava/lang/Object;)V
                    goto :revanced
                """.trimIndent(),
                ExternalLabel("revanced", getInstruction(invokeStaticRangeIndex)),
            )

            enableSettings("followBackIndicator")
        }
    }
}