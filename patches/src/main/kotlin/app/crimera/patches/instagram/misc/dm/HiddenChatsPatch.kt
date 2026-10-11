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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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
    custom = { method, classDef ->
        method.parameters.size == 1 &&
            classDef.fields.any { it.type == USER_SESSION_CLASS } &&
            method.implementation?.instructions?.any {
                it.getReference<FieldReference>()?.definingClass ==
                    "Lcom/instagram/notifications/badging/plugin/BadgingPluginImpl;"
            } == true
    },
)

// The job that builds the rows of the inbox from its list of chats; the list is the last one before the screen.
private object RowsJobFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(string("DirectThreadRowViewModelFactory.createList")),
    custom = { method, _ -> method.name == "run" },
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

// Builds the list of buttons of a chat menu, with the account session first and the list last.
private object MenuButtonsFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ ->
        method.parameters.size == 3 &&
            method.parameters.first().type == USER_SESSION_CLASS &&
            method.parameters.last().type == "Ljava/util/List;"
    },
    filters =
        listOf(
            literal(0),
            literal(25),
        ),
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

            MenuButtonsFingerprint.method.apply {
                // The method is static, so its first parameter is p0; parameters are found by type.
                val thisOffset = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                val list = parameters.indexOfFirst { it.type == "Ljava/util/List;" } + thisOffset
                val session = parameters.indexOfFirst { it.type == USER_SESSION_CLASS } + thisOffset
                addInstruction(
                    0,
                    "invoke-static {p$list, p$session}, $EXTENSION_CLASS->addMenuButton(Ljava/util/List;Ljava/lang/Object;)V",
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

            instagramInitHook.fingerprint.method.apply {
                val superIndex = instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
                addInstruction(superIndex + 1, "invoke-static {}, $EXTENSION_CLASS->init()V")
            }

            enableSettings("hiddenChats")
        }
    }
