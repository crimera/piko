/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
        default = true,
    ) {
        dependsOn(settingsPatch, directItemEntity, userDataEntity)
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

            // Hook 1: REST/JSON path. Hook 2: MQTT/MSys real-time path, which REST never touches.
            hookParsedDirectItems("$HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V")
            DeltaThreadIdFieldExtensionFingerprint.changeString(
                "deltaThreadIdField",
                hookRealtimeDirectItems(
                    "$HOOK_CLASS->onMessageReceived(Ljava/lang/Object;${USER_SESSION_CLASS}Ljava/lang/Object;)V",
                ),
            )

            // Hook 4: SQLite DAO delete, inject at entry so our DB record is still present when the hook fires.
            DirectItemDbHideFingerprint.method.apply {
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 2)
                val serverIdRegister = regs.getFreeRegister()
                val clientContextRegister = regs.getFreeRegister()

                addInstructions(
                    0,
                    """
                    move-object/from16 v$serverIdRegister, p2
                    move-object/from16 v$clientContextRegister, p3
                    invoke-static {v$serverIdRegister, v$clientContextRegister}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/String;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // REST participants supplement the cache used by real-time message delivery.
            hookThreadUsers("$HOOK_CLASS->noteThreadUsers(Ljava/util/List;)V")

            enableSettings("saveDeletedMessages")
        }
    }
