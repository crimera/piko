/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.dm.saveMessages.DirectItemDbHideFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.hookParsedDirectItems
import app.crimera.patches.instagram.misc.dm.saveMessages.hookRealtimeDirectItems
import app.crimera.patches.instagram.misc.dm.saveMessages.UserCacheIdMapFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.UserCacheInstanceFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.userCacheLookupFingerprint
import app.crimera.patches.instagram.misc.dm.saveMessages.hookThreadFields
import app.crimera.patches.instagram.misc.dm.saveMessages.hookThreadUsers
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.ghostMode.DMSeenFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val NOTIFICATION_CLASS = "Landroid/app/Notification;"
private const val NOTIFICATION_MANAGER_CLASS = "Landroid/app/NotificationManager;"
private const val ICON_COMPAT_CLASS = "Landroidx/core/graphics/drawable/IconCompat;"
private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/notification/ConversationNotifications;"
private const val CHAT_MESSAGES_CLASS = "$PATCHES_DESCRIPTOR/notification/ChatMessages;"
private const val GROUP_PHOTOS_CLASS = "$PATCHES_DESCRIPTOR/notification/GroupPhotos;"
private const val CHAT_SHORTCUTS_CLASS = "$PATCHES_DESCRIPTOR/notification/ChatShortcuts;"
private const val SHORTCUT_MANAGER_CLASS = "Landroid/content/pm/ShortcutManager;"

private object NotificationLogEnabledFingerprint : Fingerprint(
    definingClass = "$PATCHES_DESCRIPTOR/notification/NotificationLog;",
    name = "enabled",
    returnType = "Ljava/lang/String;",
)

private object ChatMessagesDeltaThreadIdFieldFingerprint : Fingerprint(
    definingClass = CHAT_MESSAGES_CLASS,
    name = "deltaThreadIdFieldName",
    returnType = "Ljava/lang/String;",
)

private fun sessionCleanupString(method: String) =
    object : Fingerprint(
        definingClass = "$PATCHES_DESCRIPTOR/notification/SessionCleanup;",
        name = method,
        returnType = "Ljava/lang/String;",
    ) {}

private object ChatMessagesUserLookupFingerprint : Fingerprint(
    definingClass = CHAT_MESSAGES_CLASS,
    name = "lookupCachedUser",
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/String;"),
    returnType = "Ljava/lang/Object;",
)

private fun chatMessagesString(method: String) =
    object : Fingerprint(definingClass = CHAT_MESSAGES_CLASS, name = method, returnType = "Ljava/lang/String;") {}

// Fetches one chat from the server, in the sync manager that also hands out itself per session.
private object ThreadSnapshotFingerprint : Fingerprint(
    returnType = "L",
    strings = listOf("IrisSyncManager requestThreadSnapshot threadId "),
)

// androidx NotificationManagerCompat.notify(String, int, Notification), which every Instagram notification goes through.
private object NotificationManagerCompatNotifyFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "I", NOTIFICATION_CLASS),
    strings = listOf("android.support.useSideChannel"),
)

// Clears the notification of a chat, which Instagram does as soon as the chat is opened.
private object RemoveDirectNotificationFingerprint : Fingerprint(
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = NOTIFICATION_MANAGER_CLASS,
            name = "cancel",
            parameters = listOf("Ljava/lang/String;", "I"),
        ),
    ),
)

// Sends a reply typed into a message notification, then updates the notification. Reply reminders
// ("rr") are removed half a second later instead of showing the reply like other chats do.
private object DirectNotificationReplyFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("push_notification_action", "rr", "type:", "LOCAL_PUSH_ID:"),
)

// Publishes the direct share shortcuts of recent chats, with the avatar Instagram draws for each chat.
private object DirectShareShortcutsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("DirectShareShortcutUtil", "actions.intent.SEND_MESSAGE"),
)

// Instagram's own dynamic shortcuts (camera, post, messages, activity, recent chats). Its updates
// replace every dynamic shortcut of the app, which drops the chat shortcuts out of the share sheet.
private object AppShortcutsFingerprint : Fingerprint(
    filters = listOf(
        methodCall(definingClass = SHORTCUT_MANAGER_CLASS, name = "removeAllDynamicShortcuts"),
        methodCall(definingClass = SHORTCUT_MANAGER_CLASS, name = "setDynamicShortcuts"),
    ),
)

// The session's shortcut cleanup, which also drops every dynamic shortcut (behind a server flag, and
// when the last account logs out; piko removes the chat shortcuts on logout itself).
// Anchored on the session interface's own method name; the class also clears shortcuts in a second,
// obfuscated method, which is rerouted with it (the whole class is).
private object SessionShortcutsFingerprint : Fingerprint(
    name = "onSessionWillEnd",
    returnType = "V",
    filters = listOf(
        methodCall(definingClass = SHORTCUT_MANAGER_CLASS, name = "removeAllDynamicShortcuts"),
    ),
)

// Modular (MDCore) unsend: the decoder class names itself in a string, and its decode method builds the payload.
private object MessageDeleteDecoderFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("IGMessageDeleteMessagingPayload"),
    custom = { _, classDef -> classDef.methods.count() <= 16 },
)

private object MessageDeleteDecodeFingerprint : Fingerprint(
    classFingerprint = MessageDeleteDecoderFingerprint,
    returnType = "L",
    parameters = listOf("L"),
    strings = listOf("__typename"),
)

// Modular (MDCore) read marker.
private object MarkReadDecoderFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf("IGMarkReadMessagingPayload"),
    custom = { _, classDef -> classDef.methods.count() <= 16 },
)

private object MarkReadDecodeFingerprint : Fingerprint(
    classFingerprint = MarkReadDecoderFingerprint,
    returnType = "L",
    parameters = listOf("L"),
    strings = listOf("__typename"),
)

// Legacy seen-state class, found by its inbox "clear unread" analytics strings; its (x, x, thread, Z)
// method queues the seen marker through a static call taking (session, state, thread, item, sender).
private object LegacySeenStateClassFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("thread_clear_unread", "direct_inbox_action"),
)

private object LegacyMarkSeenFingerprint : Fingerprint(
    classFingerprint = LegacySeenStateClassFingerprint,
    returnType = "V",
    parameters = listOf("L", "L", "L", "Z"),
    custom = { method, _ -> method.parameterTypes.first().toString() != "Landroid/content/Context;" },
)

// Legacy (Iris) seen sink: applies a participant's "has_seen" marker to a thread. Its (thread, seen,
// userId, Z) method names two of its error cases in strings.
private object LegacySeenSinkFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "Ljava/lang/String;", "Z"),
    strings = listOf("Can't find thread to update seen messages.", "DirectThreadStoreImpl_unexpectedDirectThread"),
)

/**
 * Calls [hook] (taking the payload as an Object) with what a modular payload decoder returns, and
 * returns the names of the payload's string fields at [fieldIndexes]. The decoder assigns them in
 * the order of the payload's schema, so the order of its string field writes names them. Null,
 * with nothing changed, when the decoder is not found or does not have the expected shape.
 */
context(patchContext: BytecodePatchContext)
private fun hookPayloadDecoder(decoder: Fingerprint, hook: String, fieldIndexes: List<Int>): String? {
    val method = decoder.matchOrNull()?.method ?: return null
    val instructions = method.instructions.toList()
    val fields =
        instructions
            .mapNotNull { if (it.opcode == Opcode.IPUT_OBJECT) it.getReference<FieldReference>() else null }
            .filter { it.type == "Ljava/lang/String;" }
            .groupBy { it.definingClass }
            .values
            .maxByOrNull { it.size }
            ?.map { it.name } ?: return null
    if (fieldIndexes.any { it >= fields.size }) return null
    val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_OBJECT }
    if (returns.isEmpty()) return null

    for (index in returns.reversed()) {
        val register = method.getInstruction<OneRegisterInstruction>(index).registerA
        method.addInstructionsAtControlFlowLabel(index, "invoke-static/range {v$register .. v$register}, $hook")
    }
    return fieldIndexes.joinToString(",") { fields[it] }
}

private fun String.javaName() = removePrefix("L").removeSuffix(";").replace('/', '.')

@Suppress("unused")
val conversationNotificationsPatch =
    bytecodePatch(
        name = "Conversation notifications",
        description =
            "Registers chats as Android conversations, so message notifications show in the conversations section, " +
                "can be bubbles and work in Android Auto (reply, mark as read). Shows the messages themselves instead " +
                "of \"N new messages\", with names, photos, media types and images, keeps your replies silent, and " +
                "can bundle all chats into one notification. Forgets an account's chats on logout.",
        default = true,
    ) {
        val notificationLog by booleanOption(
            key = "notificationLog",
            default = false,
            title = "Notification log",
            description =
                "Keeps a log of message notifications, including message texts and names, and of how they were " +
                    "changed, in Android/data/com.instagram.android/files/piko_notifications.log, to help fix problems.",
        )

        dependsOn(
            sharedExtensionPatch,
            directItemEntity,
            mediaDataEntity,
            userDataEntity,
            conversationNotificationsResourcePatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("conversationNotifications")
            if (notificationLog == true) {
                NotificationLogEnabledFingerprint.changeString("notificationLogEnabled", "true")
            }

            NotificationManagerCompatNotifyFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p1, p2, p3}, $EXTENSION_CLASS->onNotify(Ljava/lang/String;I$NOTIFICATION_CLASS)$NOTIFICATION_CLASS
                move-result-object p3
                """,
            )

            RemoveDirectNotificationFingerprint.method.apply {
                val cancelIndex =
                    instructions.indexOfFirst {
                        val reference = it.getReference<MethodReference>()
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            reference?.definingClass == NOTIFICATION_MANAGER_CLASS &&
                            reference.name == "cancel" &&
                            reference.parameterTypes == listOf("Ljava/lang/String;", "I")
                    }
                if (cancelIndex < 0) throw PatchException("Missing where a chat's notification is cancelled")

                val registers = instructions[cancelIndex].registersUsed
                replaceInstruction(
                    cancelIndex,
                    "invoke-static {v${registers[0]}, v${registers[1]}, v${registers[2]}}, " +
                        "$EXTENSION_CLASS->cancel(${NOTIFICATION_MANAGER_CLASS}Ljava/lang/String;I)V",
                )
            }

            // A reply to a group digest updates the notification like any other chat instead of removing it.
            DirectNotificationReplyFingerprint.method.apply {
                val reminderIndex = instructions.indexOfFirst { it.getReference<StringReference>()?.string == "rr" }
                if (reminderIndex < 0) throw PatchException("Missing the reply reminder check")

                val isReminderIndex =
                    (reminderIndex + 1 until instructions.size).firstOrNull {
                        val reference = instructions[it].getReference<MethodReference>()
                        reference?.definingClass == "Ljava/lang/String;" && reference.name == "equals"
                    }?.plus(1) ?: throw PatchException("Missing the reply reminder comparison")
                val isReminder = instructions[isReminderIndex]
                if (isReminder.opcode != Opcode.MOVE_RESULT || instructions[isReminderIndex + 1].opcode != Opcode.IF_EQZ) {
                    throw PatchException("The reply reminder check has an unexpected shape")
                }
                val isReminderRegister = (isReminder as OneRegisterInstruction).registerA
                if (isReminderRegister > 0xF) throw PatchException("The reply reminder check needs a 4-bit register")

                replaceInstruction(isReminderIndex, "const/4 v$isReminderRegister, 0x0")
            }

            // Group notifications only carry a count, so the messages are taken as Instagram receives them.
            hookParsedDirectItems("$CHAT_MESSAGES_CLASS->onMessageReceived(Ljava/lang/Object;)V")
            ChatMessagesDeltaThreadIdFieldFingerprint.changeString(
                "deltaThreadIdField",
                hookRealtimeDirectItems(
                    "$CHAT_MESSAGES_CLASS->onMessageReceived(Ljava/lang/Object;${USER_SESSION_CLASS}Ljava/lang/Object;)V",
                ),
            )
            // Group icons: the thread's own photo, else its members' photos, keyed by the thread's id.
            hookThreadUsers(
                "$CHAT_MESSAGES_CLASS->onThreadUsers(Ljava/util/List;)V",
                "$GROUP_PHOTOS_CLASS->onThreadMembers(Ljava/lang/Object;Ljava/util/List;)V",
            )
            if (!hookThreadFields(
                    "$GROUP_PHOTOS_CLASS->onThreadId(Ljava/lang/Object;Ljava/lang/String;)V",
                    "$GROUP_PHOTOS_CLASS->onThreadImage(Ljava/lang/Object;Ljava/lang/Object;)V",
                )
            ) {
                println("WARNING: Found no thread id or photo in the thread parser, groups keep letter icons")
            }

            // Group digests only carry a count, so the chat is fetched when its messages are missing.
            val syncManager = ThreadSnapshotFingerprint.classDef
            val syncManagerGetter =
                syncManager.methods.singleOrNull {
                    AccessFlags.STATIC.isSet(it.accessFlags) &&
                        it.returnType == syncManager.type &&
                        it.parameterTypes.map(CharSequence::toString) == listOf(USER_SESSION_CLASS)
                } ?: throw PatchException("Missing the sync manager getter")
            syncManagerGetter.addInstructions(
                0,
                "invoke-static {p0}, $CHAT_MESSAGES_CLASS->rememberSession(${USER_SESSION_CLASS})V",
            )
            // Chats in the share sheet: piko's start hook registers the share rewrite before any
            // activity, as a share can start the app (see ShareTargets).
            instagramInitHook.fingerprint.method.apply {
                val superIndex = indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER)
                val application = getInstruction(superIndex).registersUsed[0]
                addInstructions(
                    superIndex + 1,
                    "invoke-static/range {v$application .. v$application}, $PATCHES_DESCRIPTOR/notification/ShareTargets;" +
                        "->onAppCreate(Landroid/content/Context;)V",
                )
            }

            // Forget an account on logout: the interface a session calls on its objects when it ends
            // (the instanceof check in completeEndSession). Optional: without it, state ages out in 7 days.
            runCatching {
                val end = classDefBy(USER_SESSION_CLASS).methods.single { it.name == "completeEndSession" }
                val listener = end.implementation!!.instructions
                    .filter { it.opcode == Opcode.INSTANCE_OF }
                    .mapNotNull { it.getReference<TypeReference>()?.type }
                    .first { type -> classDefBy(type).methods.any { it.name == "onUserSessionWillEnd" } }
                sessionCleanupString("sessionEndInterface").changeString("sessionEndInterface", listener.javaName())
            }.onFailure { println("WARNING: session end interface not found (${it.message}), logout does not clear chats") }

            // Instagram's own users, which name the senders of rebuilt messages: the stub becomes
            // UserCache.<getter>(session).<lookup by id>(id), with the lookups Save deleted messages
            // uses. Optional: without it names come from notifications, members and the directory.
            runCatching {
                val cacheInstance = UserCacheInstanceFingerprint.originalMethod
                val idMap = UserCacheIdMapFingerprint.instructionMatches.last().fieldReference
                val cacheLookup = userCacheLookupFingerprint(idMap).originalMethod
                ChatMessagesUserLookupFingerprint.method.apply {
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
            }.onFailure { println("WARNING: UserCache lookup not found (${it.message}), names come from notifications only") }

            chatMessagesString("syncManagerClass").changeString("syncManagerClass", syncManager.type.javaName())
            chatMessagesString("threadSnapshotMethod").changeString("threadSnapshotMethod", ThreadSnapshotFingerprint.method.name)
            // Queues the marker that tells the server a chat was read (shared with ghost mode and mark as read).
            chatMessagesString("markSeenClass").changeString("markSeenClass", DMSeenFingerprint.classDef.type.javaName())
            chatMessagesString("markSeenMethod").changeString("markSeenMethod", DMSeenFingerprint.method.name)

            // Message lifecycle. Each hook is optional: an Instagram update that moves one only loses it.
            // Unsend, legacy route: the message database hides the item by id.
            DirectItemDbHideFingerprint.matchOrNull()?.method?.addInstructions(
                0,
                "invoke-static/range {p1 .. p3}, " +
                    "$CHAT_MESSAGES_CLASS->onItemHidden(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V",
            )
            // Unsend, modular route: thread, offline id, message_igid, message_id are written in that order.
            hookPayloadDecoder(
                MessageDeleteDecodeFingerprint,
                "$CHAT_MESSAGES_CLASS->onModularDelete(Ljava/lang/Object;)V",
                listOf(0, 2),
            )?.let { chatMessagesString("deleteFields").changeString("deleteFields", it) }
            // Read marker, modular route: thread, offline id, item, read ms, read us.
            hookPayloadDecoder(
                MarkReadDecodeFingerprint,
                "$CHAT_MESSAGES_CLASS->onModularRead(Ljava/lang/Object;)V",
                listOf(0, 2, 3),
            )?.let { chatMessagesString("readFields").changeString("readFields", it) }
            // Read marker, legacy route: before the seen marker is queued, with the chat and item.
            LegacyMarkSeenFingerprint.matchOrNull()?.method?.let { seen ->
                val instructions = seen.instructions.toList()
                for (index in instructions.indices.reversed()) {
                    val reference = instructions[index].getReference<MethodReference>()
                    if (instructions[index].opcode != Opcode.INVOKE_STATIC || reference == null) continue
                    val types = reference.parameterTypes.map(CharSequence::toString)
                    if (types.size != 5 || types.drop(2).any { it != "Ljava/lang/String;" } || reference.returnType != "V") continue
                    val registers = instructions[index].registersUsed
                    seen.addInstructionsAtControlFlowLabel(
                        index,
                        "invoke-static {v${registers[2]}, v${registers[3]}}, " +
                            "$CHAT_MESSAGES_CLASS->onLegacyRead(Ljava/lang/String;Ljava/lang/String;)V",
                    )
                }
            }

            // Read on another device, legacy route: Iris applies the account's seen marker through the
            // sink, and snapshots through the thread's own (seen, userId) method that the sink also calls.
            // Both are optional.
            LegacySeenSinkFingerprint.matchOrNull()?.method?.let { sink ->
                val applier =
                    sink.instructions.firstNotNullOfOrNull {
                        val reference = it.getReference<MethodReference>()
                        if (it.opcode == Opcode.INVOKE_VIRTUAL && reference != null && reference.returnType == "Z" &&
                            reference.parameterTypes.size == 2 && reference.parameterTypes[1] == "Ljava/lang/String;" &&
                            reference.definingClass != sink.definingClass
                        ) reference else null
                    }
                sink.addInstructions(
                    0,
                    "invoke-static/range {p0 .. p3}, $CHAT_MESSAGES_CLASS->onSeenIris(" +
                        "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                )
                if (applier != null) {
                    runCatching {
                        mutableClassDefBy(applier.definingClass).methods.firstOrNull {
                            it.name == applier.name && it.parameterTypes.map(CharSequence::toString) ==
                                applier.parameterTypes.map(CharSequence::toString)
                        }?.addInstructions(
                            0,
                            "invoke-static/range {p0 .. p2}, $CHAT_MESSAGES_CLASS->onSeenSnapshot(" +
                                "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                        )
                    }
                }
            }

            // Instagram's shortcut updates go through piko, which keeps the chat shortcuts dynamic.
            val shortcutCalls = mapOf(
                "removeAllDynamicShortcuts" to "()V",
                "removeDynamicShortcuts" to "(Ljava/util/List;)V",
                "setDynamicShortcuts" to "(Ljava/util/List;)Z",
                "addDynamicShortcuts" to "(Ljava/util/List;)Z",
            )
            var rerouted = 0
            val shortcutClasses = listOfNotNull(
                AppShortcutsFingerprint.matchOrNull()?.classDef,
                SessionShortcutsFingerprint.matchOrNull()?.classDef,
            )
            shortcutClasses.flatMap { it.methods }.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                // Collected first, then replaced: same size, so the indices stay valid.
                val calls = implementation.instructions.withIndex().filter { (_, instruction) ->
                    val reference = instruction.getReference<MethodReference>()
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        reference?.definingClass == SHORTCUT_MANAGER_CLASS && reference.name in shortcutCalls
                }.toList()
                for ((index, instruction) in calls) {
                    val reference = instruction.getReference<MethodReference>()!!
                    val signature = shortcutCalls.getValue(reference.name)
                    val registers = instruction.registersUsed.joinToString(", ") { "v$it" }
                    val parameters = signature.substringAfter('(').substringBefore(')')
                    method.replaceInstruction(
                        index,
                        "invoke-static {$registers}, $CHAT_SHORTCUTS_CLASS->${reference.name}(" +
                            "$SHORTCUT_MANAGER_CLASS$parameters)${signature.substringAfter(')')}",
                    )
                    rerouted++
                }
            }
            if (rerouted == 0) {
                println("WARNING: Found no shortcut update to keep chats in, Instagram's updates drop them from the share sheet")
            }

            // Optional: only the group avatars Instagram draws for its share shortcuts.
            DirectShareShortcutsFingerprint.matchOrNull()?.method?.apply {
                val iconIndex =
                    instructions.indexOfFirst {
                        val reference = it.getReference<MethodReference>()
                        it.opcode == Opcode.INVOKE_STATIC &&
                            reference?.definingClass == ICON_COMPAT_CLASS &&
                            reference.name == "createWithBitmap"
                    }
                if (iconIndex < 0) {
                    println("WARNING: Missing the avatar of a direct share shortcut, group icons skip Instagram's avatar")
                    return@apply
                }
                val avatarRegister = instructions[iconIndex].registersUsed.single()

                // The chat id is read right after the icon is made, into a register that is free until then.
                val threadIdIndex =
                    (iconIndex + 1 until instructions.size).firstOrNull {
                        val reference = instructions[it].getReference<MethodReference>()
                        (instructions[it].opcode == Opcode.INVOKE_INTERFACE ||
                            instructions[it].opcode == Opcode.INVOKE_INTERFACE_RANGE) &&
                            reference?.parameterTypes?.isEmpty() == true &&
                            reference.returnType == "Ljava/lang/String;"
                    } ?: run {
                        println("WARNING: Missing the chat id of a direct share shortcut, group icons skip Instagram's avatar")
                        return@apply
                    }
                val threadIdCall = instructions[threadIdIndex]
                val chatRegister = threadIdCall.registersUsed.single()
                val threadIdRegister = (instructions[threadIdIndex + 1] as OneRegisterInstruction).registerA
                if (avatarRegister > 0xF || threadIdRegister > 0xF) {
                    println("WARNING: The avatar and chat id of a direct share shortcut need 4-bit registers, skipped")
                    return@apply
                }

                addInstructions(
                    iconIndex + 2,
                    """
                    invoke-interface/range {v$chatRegister .. v$chatRegister}, ${threadIdCall.getReference<MethodReference>()}
                    move-result-object v$threadIdRegister
                    invoke-static {v$threadIdRegister, v$avatarRegister}, $PATCHES_DESCRIPTOR/notification/ChatShortcuts;->onChatAvatar(Ljava/lang/String;Landroid/graphics/Bitmap;)V
                    """,
                )
            }
        }
    }
