/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.DirectItemJsonParserFingerprint
import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.mediadata.mediaUrlPatch
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.registersUsed
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.AccessFlags

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    patch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
        default = true,
    ) {
        dependsOn(settingsPatch, directItemEntity, userDataEntity, mediaUrlPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val cacheInstance = UserCacheInstanceFingerprint.originalMethod
            val idMap = UserCacheIdMapFingerprint.instructionMatches.last().fieldReference
            val cacheLookup = userCacheLookupFingerprint(idMap).originalMethod
            CachedUserLookupExtensionFingerprint.method.apply {
                removeInstructions(0, instructions.size)
                addInstructions(
                    0,
                    """
                    invoke-static/range {p0 .. p0}, $cacheInstance
                    move-result-object p0
                    invoke-virtual/range {p0 .. p1}, $cacheLookup
                    move-result-object p0
                    return-object p0
                    """.trimIndent(),
                )
            }

            // Hook 1: REST/JSON path, inject at the return of the parser.
            DirectItemJsonParserFingerprint.let {
                val returnObject = it.instructionMatches.last()
                val itemRegister = returnObject.instruction.registersUsed.single()

                it.method.addInstructionsAtControlFlowLabel(
                    returnObject.index,
                    """
                    invoke-static {v$itemRegister}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Hook 2: MQTT/MSys real-time path, which REST never touches. Hook at entry because
            // normal branches jump directly to return instructions and skip code inserted before them.
            val postprocess = DirectItemPostprocessFingerprint.method
            val deltaType = postprocess.parameterTypes[1].toString()
            val deltaStringFields =
                classDefBy(deltaType)
                    .fields.filter { field ->
                        field.type == "Ljava/lang/String;" &&
                            !AccessFlags.STATIC.isSet(field.accessFlags)
                    }
            if (deltaStringFields.size != 1) {
                throw PatchException(
                    "Expected one delta thread-id field in $deltaType, " +
                        "found ${deltaStringFields.size}",
                )
            }
            DeltaThreadIdFieldExtensionFingerprint.changeString(
                "deltaThreadIdField",
                deltaStringFields.single().name,
            )
            postprocess.addInstructions(
                0,
                """
                invoke-static/range {p0 .. p2}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                """.trimIndent(),
            )

            DirectItemDbHideFingerprint.let {
                val serverId = it.instructionMatches[1].instruction.registersUsed.first()
                val clientContext = it.instructionMatches[2]
                val clientId = clientContext.instruction.registersUsed.first()
                it.method.addInstructions(
                    clientContext.index + 1,
                    "invoke-static {v$serverId, v$clientId}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/String;Ljava/lang/String;)V",
                )
            }

            // The recipients array stores wrappers. Capture the User before it is wrapped.
            ThreadRecipientFingerprint.let {
                val result = it.instructionMatches[2]
                val user = result.instruction.registersUsed.single()
                it.method.addInstructions(
                    result.index + 1,
                    "invoke-static/range {v$user .. v$user}, $HOOK_CLASS->noteThreadUser(Ljava/lang/Object;)V",
                )
            }

            enableSettings("saveDeletedMessages")
        }
    }
