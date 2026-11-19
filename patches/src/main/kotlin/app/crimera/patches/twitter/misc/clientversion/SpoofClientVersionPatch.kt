/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.clientversion

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/clientversion/SpoofClientVersionPatch;"

private object GraphQLOperationsFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/xchat/",
    name = "name",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)

// XChat requests go through the legacy header builder (com.twitter.network.h1).
private object LegacyClientHeaderBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/network/",
    returnType = "V",
    parameters = listOf("Ljava/net/URI;", "Lcom/twitter/util/user/UserIdentifier;", "Lcom/twitter/network/"),
    filters = listOf(
        string("User-Agent"),
        methodCall(name = "toString", location = InstructionLocation.MatchAfterWithin(2)),
        string("X-Twitter-Client-Version"),
        string(packageMetadata.versionName, location = InstructionLocation.MatchAfterWithin(2)),
    ),
)

@Suppress("unused")
val spoofClientVersionPatch =
    patch(
        name = "Spoof client version",
        description = "Reports a newer app version to XChat's servers, which refuse old versions with " +
            "\"Update Required\" (ClientRequiresUpdate). The rest of the app keeps reporting its real version.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        category("Old patches")
        dependsOn(sharedExtensionPatch)

        val spoofedVersion by stringOption(
            key = "spoofedVersion",
            default = "12.44.0-prod.01",
            title = "Reported version name",
            description = "Version name XChat sees instead of the app's own.",
            required = true,
        )

        val spoofedVersionCode by intOption(
            key = "spoofedVersionCode",
            default = 312440001,
            title = "Reported version code",
            description = "Version code XChat sees. It must belong to the reported version name.",
            required = true,
        )

        execute {
            val version = spoofedVersion!!
            val versionCode = spoofedVersionCode!!

            // 1. Extension values come from the patch options.
            Fingerprint(definingClass = EXTENSION_CLASS_DESCRIPTOR, name = "version").method
                .returnEarly(version)
            Fingerprint(definingClass = EXTENSION_CLASS_DESCRIPTOR, name = "versionCode").method
                .returnEarly(versionCode)

            // 2. Retrieve XChat's GraphQL operations (sending messages, inbox, keys…) whose paths don't say "xchat".
            val operations = sortedSetOf<String>()
            val operationRegex = Regex("[A-Za-z]+")
            GraphQLOperationsFingerprint.matchAll().forEach {
                // All matched methods contain exactly two instructions: const-string and return-object.
                val operation = ((it.originalMethod.implementation?.instructions?.first() as ReferenceInstruction).reference as StringReference).string
                if (!operation.matches(operationRegex)) throw PatchException("Unexpected operation name $operation")
                operations.add(operation)
            }
            if ("SendMessageCreateMutation" !in operations) throw PatchException("XChat operations not found ($operations)")
            Fingerprint(definingClass = EXTENSION_CLASS_DESCRIPTOR, name = "xchatOperations")
                .method.returnEarly(operations.joinToString(","))

            // 3. Spoof client version only for XChat requests.
            LegacyClientHeaderBuilderFingerprint.let {
                it.method.apply {
                    // Insert from the bottom up so earlier indices stay valid.

                    // "X-Twitter-Client-Version" header
                    val versionIndex = it.instructionMatches.last().index
                    val isXChatRegister = findFreeRegister(versionIndex + 1)
                    val versionRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
                    addInstructionsWithLabels(
                        versionIndex + 1,
                        """
                        if-eqz v$isXChatRegister, :not_xchat
                        const-string v$versionRegister, "$version"
                        :not_xchat
                        nop
                        """
                    )

                    // "User-Agent" header
                    val userAgentMoveResultIndex = it.instructionMatches[1].index + 1
                    val userAgentRegister = getInstruction<OneRegisterInstruction>(userAgentMoveResultIndex).registerA
                    addInstructionsWithLabels(
                        userAgentMoveResultIndex + 1,
                        """
                        if-eqz v$isXChatRegister, :not_xchat
                        invoke-static {v$userAgentRegister}, $EXTENSION_CLASS_DESCRIPTOR->userAgent(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$userAgentRegister
                        :not_xchat
                        nop
                        """
                    )

                    addInstructions(
                        0,
                        """
                        invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->isXChatRequest(Ljava/net/URI;)Z
                        move-result v$isXChatRegister
                        """
                    )
                }
            }
        }
    }