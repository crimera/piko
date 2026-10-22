/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/dm/HiddenChats;"

// Builds and shows the notification of a message push; it gets the account session, a callback and the chat id.
private object MessageNotificationFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("build_notification_start"),
            string("after_notification_displayed_start"),
        ),
    custom = { method, _ ->
        method.parameters.any { it.type == "Ljava/lang/Runnable;" }
    },
)

// Sets the unread count on the messages button; its class holds the account session and uses the badging plugin.
private object BadgeSetterFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters =
        listOf(
            fieldAccess(definingClass = "Lcom/instagram/notifications/badging/plugin/BadgingPluginImpl;"),
        ),
    custom = { _, classDef ->
        classDef.fields.any { it.type == USER_SESSION_CLASS }
    },
)

// The badge object and its state, taken from the badge setter before the two fingerprints below are read.
private var badgeType = ""
private var stateType = ""

// Builds the badge state from the sorted chats; it cuts the list, drops the muted chats and caps the count.
private object BadgeStateBuilderFingerprint : Fingerprint(
    custom = { method, _ ->
        method.returnType == stateType &&
            method.parameters.isEmpty() &&
            (method.implementation?.instructions?.count {
                val call = it.getReference<MethodReference>()
                call?.definingClass == "Ljava/lang/Math;" && call.name == "min"
            } ?: 0) >= 2
    },
)

// Works the badge state out from a message event, with a list of its own: it makes a state and a badge from it.
private object BadgeEventFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    custom = { method, _ ->
        method.implementation?.instructions?.let { code ->
            code.any { it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == badgeType } &&
                code.any { it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == stateType }
        } == true
    },
)

// The job that works out the badge state again and posts it to the screen.
private object BadgeJobFingerprint : Fingerprint(
    returnType = "V",
    name = "run",
    parameters = emptyList(),
    custom = { method, _ ->
        method.implementation?.instructions?.let { code ->
            code.any { it.getReference<MethodReference>()?.returnType == stateType } &&
                code.any { it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == badgeType } &&
                code.any { it.getReference<MethodReference>()?.name == "post" }
        } == true
    },
)

// The job that builds the rows of the inbox from its list of chats; the list is the last one before the screen.
private object RowsJobFingerprint : Fingerprint(
    returnType = "V",
    name = "run",
    parameters = emptyList(),
    filters = listOf(string("DirectThreadRowViewModelFactory.createList")),
)

// The buttons of the chat menu are constants of an enum, found by the names of three of them.
private object MenuButtonsEnumFingerprint : Fingerprint(
    filters =
        listOf(
            string("MARK_AS_READ"),
            string("LEAVE"),
            string("NFB_SETTINGS"),
        ),
)

// Opens the menu of a chat; the other opener of the labels (bulk actions) lives elsewhere and has no such call.
private object MenuOpenFingerprint : Fingerprint(
    classFingerprint = MenuButtonsLabelsFingerprint,
    returnType = "V",
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameters.firstOrNull()?.type == "Landroid/graphics/RectF;" &&
            method.parameters.count { it.type == "Ljava/util/List;" } == 1
    },
)

// Lists the names of the buttons; the class it belongs to also handles their press.
private object MenuButtonsLabelsFingerprint : Fingerprint(
    returnType = "[Ljava/lang/String;",
    filters = listOf(string("[DEBUG] Thread info")),
)

// Handles the press of a chat menu button.
private object MenuButtonActionFingerprint : Fingerprint(
    classFingerprint = MenuButtonsLabelsFingerprint,
    filters =
        listOf(
            string("direct_inbox_action"),
            string("thread_unflag"),
        ),
)

// The badge object holds the count and the state of the unread chats; the state is the field that is not a number or text.
context(context: BytecodePatchContext)
private fun badgeStateType(badgeType: String): String =
    context.mutableClassDefBy(badgeType)
        .fields
        .first { it.type != "I" && it.type != "Ljava/lang/String;" }
        .type

// The state keeps several lists of chats; the counted one is the second list its constructor receives.
context(context: BytecodePatchContext)
private fun countedListOf(stateType: String): String {
    val constructor = context.mutableClassDefBy(stateType).methods.first { it.name == "<init>" }
    val implementation = constructor.implementation!!
    // Parameters sit in the last registers (longs take two), so the register of the list is counted from there.
    fun width(type: String) = if (type == "J" || type == "D") 2 else 1
    val parameters = constructor.parameters.map { it.type }
    val second = parameters.indices.filter { parameters[it] == "Ljava/util/List;" }[1]
    val register = implementation.registerCount - parameters.sumOf(::width) + parameters.take(second).sumOf(::width)
    return implementation.instructions
        .first { it.opcode == Opcode.IPUT_OBJECT && (it as TwoRegisterInstruction).registerA == register }
        .getReference<FieldReference>()!!
        .name
}

@Suppress("unused")
val hiddenChatsPatch =
    bytecodePatch(
        name = "Hidden chats",
        description = "Adds an option to the chat menu that moves the chat to a private inbox, opened by tapping the messages button several times.",
        default = true,
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // The job's register for itself is above v15, so the call takes it as a range.
            RowsJobFingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->filterRows(Ljava/lang/Object;)V",
            )

            MenuOpenFingerprint.method.apply {
                // The method is static; its list is replaced by one with the extra button before anything uses it.
                val list = parameters.indexOfFirst { it.type == "Ljava/util/List;" }
                val menu = parameters.indexOfFirst { it.type == MenuButtonsLabelsFingerprint.classDef.type }
                // The registers are far from v0, so they are copied to the free v0 and v1 to be passed on.
                addInstructions(
                    0,
                    """
                    move-object/from16 v0, p$list
                    move-object/from16 v1, p$menu
                    invoke-static {v0, v1}, $EXTENSION_CLASS->menuOpening(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
                    move-result-object v0
                    move-object/from16 p$list, v0
                    """.trimIndent(),
                )
            }

            val buttonEnum = MenuButtonsEnumFingerprint.classDef.type
            MenuButtonActionFingerprint.apply {
                val session = classDef.fields.first { it.type == USER_SESSION_CLASS }
                method.apply {
                    val button = parameters.indexOfFirst { it.type == buttonEnum } + 1
                    val key = parameters.indexOfFirst { it.type == "Lcom/instagram/model/direct/DirectThreadKey;" } + 1
                    // Registers are fixed because the code goes first and nothing is live yet.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v1, v0, $session
                        move-object/from16 v2, p$button
                        move-object/from16 v3, p$key
                        invoke-static {v1, v2, v3}, $EXTENSION_CLASS->onMenuButton(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko_menu
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko_menu", getInstruction(0)),
                    )
                }
            }

            MessageNotificationFingerprint.method.apply {
                val thisOffset = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                val session = thisOffset
                val callback = parameters.indexOfFirst { it.type == "Ljava/lang/Runnable;" } + thisOffset
                val chat = parameters.indexOfFirst { it.type == "Ljava/lang/String;" } + thisOffset
                val first = instructions.first()
                // The parameter registers are above v15, so they are copied to v0-v2 (unused at the start) to be passed on.
                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v0, p$session
                    move-object/from16 v1, p$callback
                    move-object/from16 v2, p$chat
                    invoke-static {v0, v1, v2}, $EXTENSION_CLASS->shouldMuteNotification(Ljava/lang/Object;Ljava/lang/Runnable;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :piko_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_continue", first),
                )
            }

            BadgeSetterFingerprint.method.apply {
                badgeType = parameters.first().type
                stateType = badgeStateType(badgeType)
                val first = instructions.first()
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {p0, p1}, $EXTENSION_CLASS->onBadge(Ljava/lang/Object;Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :piko_badge
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_badge", first),
                )
            }

            BadgeStateBuilderFingerprint.matchSingle().method.apply {
                val session = instructions.first { it.getReference<FieldReference>()?.type == USER_SESSION_CLASS }
                    .let { (it as OneRegisterInstruction).registerA }
                // The hidden chats are taken out of the two lists the state is made from, right after each is read.
                val sites = mutableListOf<Int>()
                instructions.forEachIndexed { index, instruction ->
                    val call = instruction.getReference<MethodReference>() ?: return@forEachIndexed
                    val chats = (instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        call.returnType == "Ljava/util/List;" &&
                        call.parameterTypes.size == 4 &&
                        call.parameterTypes.last() == "Z") ||
                        (call.definingClass == "Ljava/util/Collections;" && call.name == "unmodifiableList")
                    if (chats && instructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT) sites += index + 1
                }
                if (sites.isEmpty()) throw PatchException("Chat lists of the badge state not found")
                for (site in sites.sortedDescending()) {
                    val list = (instructions[site] as OneRegisterInstruction).registerA
                    addInstructions(
                        site + 1,
                        """
                        invoke-static {v$list, v$session}, $EXTENSION_CLASS->filterThreads(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
                        move-result-object v$list
                        """.trimIndent(),
                    )
                }
            }

            BadgeJobFingerprint.method.addInstruction(0, "invoke-static {p0}, $EXTENSION_CLASS->badgeJob(Ljava/lang/Object;)V")
            BadgeEventFingerprint.matchSingle().method.apply {
                // The chats it counts come from a list of its own: the hidden ones are taken out of it right where it is read.
                val session = instructions.first { it.getReference<FieldReference>()?.type == USER_SESSION_CLASS }
                    .let { (it as OneRegisterInstruction).registerA }
                val cast = instructions.indexOfFirst {
                    it.opcode == Opcode.CHECK_CAST && it.getReference<TypeReference>()?.type == "Ljava/util/List;"
                }
                if (cast < 0) throw PatchException("Chat list of the badge event not found")
                val list = (instructions[cast] as OneRegisterInstruction).registerA
                addInstructions(
                    cast + 1,
                    """
                    invoke-static {v$list, v$session}, $EXTENSION_CLASS->filterThreads(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
                    move-result-object v$list
                    """.trimIndent(),
                )
                // The registers of this method go past v15, so the call takes its argument as a range.
                addInstruction(0, "invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->badgeEvent(Ljava/lang/Object;)V")
            }

            // Every badge state goes through the constructor of the badge, whoever built it, so it is cleaned there.
            val counted = countedListOf(stateType)
            mutableClassDefBy(badgeType).methods.first { it.name == "<init>" }.addInstructions(
                0,
                """
                const-string v0, "$counted"
                invoke-static {p1, v0}, $EXTENSION_CLASS->filterState(Ljava/lang/Object;Ljava/lang/String;)V
                """.trimIndent(),
            )

            instagramInitHook.fingerprint.method.apply {
                val superIndex = instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
                addInstruction(superIndex + 1, "invoke-static {}, $EXTENSION_CLASS->init()V")
            }

            enableSettings("hiddenChats")
        }
    }
