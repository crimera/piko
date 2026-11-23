/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.entity.directItem.DirectThreadKeyToStringFingerprint
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.dm.saveMessages.ThreadRecipientFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.UserCacheIdMapFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.UserCacheInstanceFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.userCacheLookupFingerprint
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/dm/DmNotificationAllowlist;"
private const val DIRECT_THREAD_KEY_CLASS = "Lcom/instagram/model/direct/DirectThreadKey;"

private object AllowlistCachedUserLookupExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "lookupCachedUser",
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/String;"),
    returnType = "Ljava/lang/Object;",
)

private object AllowlistThreadKeyIdFieldExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "threadKeyIdFieldName",
    returnType = "Ljava/lang/String;",
)

private object AllowlistThreadKeyRecipientsFieldExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "threadKeyRecipientsFieldName",
    returnType = "Ljava/lang/String;",
)

// Builds and shows the notification of a message push; it gets the account session, a callback and the chat id.
private object AllowlistMessageNotificationFingerprint : Fingerprint(
    isStatic = false,
    returnType = "V",
    parameters =
        listOf(
            "L", // Account session
            "Ljava/lang/Runnable;", // Callback
            "Ljava/lang/String;", // Chat id
            "Z",
            "Z",
        ),
    filters =
        listOf(
            string("build_notification_start"),
            string("after_notification_displayed_start"),
        ),
)

// NotificationManagerCompat.notify(tag, id, notification), used as a fallback when an unseen thread arrives for username matching.
private object AllowlistNotifyFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "I", "Landroid/app/Notification;"),
    filters =
        listOf(
            string("android.support.useSideChannel"),
        ),
)

// The job that builds the rows of the inbox from its list of chats; used read-only to index threadId -> participants.
private object AllowlistInboxRowsJobFingerprint : Fingerprint(
    returnType = "V",
    name = "run",
    parameters = emptyList(),
    filters = listOf(string("DirectThreadRowViewModelFactory.createList")),
)

@Suppress("unused")
val dmNotificationAllowlistPatch =
    patch(
        name = "DM notification allowlist",
        description = "Allows DM notifications only from selected people or chats.",
        default = true,
    ) {
        dependsOn(settingsPatch, userDataEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // Wire session-scoped UserCache lookup so inbox thread participants resolve to usernames/full names.
            val cacheInstance = UserCacheInstanceFingerprint.originalMethod
            val idMap = UserCacheIdMapFingerprint.instructionMatches.last().fieldReference
            val cacheLookup = userCacheLookupFingerprint(idMap).originalMethod
            AllowlistCachedUserLookupExtensionFingerprint.method.apply {
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

            // Resolve DirectThreadKey's threadId and recipient list field names at patch time.
            val threadIdField = DirectThreadKeyToStringFingerprint.instructionMatches.first().fieldReference
            AllowlistThreadKeyIdFieldExtensionFingerprint.changeFirstString(threadIdField.name)
            classDefBy(DIRECT_THREAD_KEY_CLASS)
                .fields
                .singleOrNull { it.type == "Ljava/util/List;" && !AccessFlags.STATIC.isSet(it.accessFlags) }
                ?.let { recipientsField ->
                    AllowlistThreadKeyRecipientsFieldExtensionFingerprint.changeFirstString(recipientsField.name)
                }

            // Gate 1: Primary DM push notification hook before the notification is built.
            AllowlistMessageNotificationFingerprint.method.apply {
                val resultRegister = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static/range {p1 .. p3}, $EXTENSION_CLASS->shouldMuteNotification(Ljava/lang/Object;Ljava/lang/Runnable;Ljava/lang/String;)Z
                    move-result v$resultRegister
                    if-eqz v$resultRegister, :piko_dm_allowlist_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_dm_allowlist_continue", instructions.first()),
                )
            }

            // Gate 2: Fallback hook on NotificationManagerCompat.notify for unseen threads when allowlisting by username.
            AllowlistNotifyFingerprint.method.apply {
                val tag = parameters.indexOfFirst { it.type == "Ljava/lang/String;" } + 1
                val notification = parameters.indexOfFirst { it.type == "Landroid/app/Notification;" } + 1
                val resultRegister = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {p$tag, p$notification}, $EXTENSION_CLASS->shouldMutePostedNotification(Ljava/lang/String;Landroid/app/Notification;)Z
                    move-result v$resultRegister
                    if-eqz v$resultRegister, :piko_dm_allowlist_notify
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_dm_allowlist_notify", instructions.first()),
                )
            }

            // Index inbox chats in the background so users can pick chats in settings and threadId -> user matching stays fresh.
            AllowlistInboxRowsJobFingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->indexInboxRows(Ljava/lang/Object;)V",
            )

            // Supplement userId -> username directory when DM thread recipients are parsed from JSON.
            ThreadRecipientFingerprint.matchOrNull()?.let { match ->
                val result = match.instructionMatches[2]
                val user = result.instruction.registersUsed.single()
                match.method.addInstructions(
                    result.index + 1,
                    "invoke-static/range {v$user .. v$user}, $EXTENSION_CLASS->noteThreadUser(Ljava/lang/Object;)V",
                )
            }

            enableSettings("dmNotificationAllowlist")
        }
    }
