/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.chatColors

import app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch.ThreadLongPressButtonStringListFingerprint
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral

internal const val THEME_INFO_CLASS = "Lcom/instagram/direct/model/DirectThreadThemeInfo;"
internal const val THREAD_KEY_CLASS = "Lcom/instagram/model/direct/DirectThreadKey;"
internal const val EXTENSION_CLASS ="$PATCHES_DESCRIPTOR/customise/chatColors/ChatColors;"

/**
 * The method every part of a chat reads its theme through. It returns the theme Instagram keeps for the chat, or a
 * shared default one when the chat has none, and the class holding it also holds the key of the chat.
 */
internal object ThreadThemeGetterFingerprint : Fingerprint(
    returnType = THEME_INFO_CLASS,
    parameters = emptyList(),
    custom = { method, classDef ->
        method.implementation != null && classDef.fields.any { it.type == THREAD_KEY_CLASS }
    },
)

/**
 * Writes a theme as JSON. Each field is written under the key the server sends it as, which does not change between
 * versions the way the names of the fields do.
 */
internal object ThemeJsonWriterFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", THEME_INFO_CLASS),
    strings = listOf("light_primary_text_message_from_me_color", "light_theme_incoming_message_bubble_color"),
)

/**
 * Opens the menu of a chat with the list of its buttons. The other opener of the menu labels, for the bulk actions,
 * lives elsewhere and has no such list.
 */
internal object ChatMenuOpenFingerprint : Fingerprint(
    classFingerprint = ThreadLongPressButtonStringListFingerprint,
    isStatic = true,
    returnType = "V",
    custom = { method, _ ->
        method.parameters.firstOrNull()?.type == "Landroid/graphics/RectF;" &&
            method.parameters.count { it.type == "Ljava/util/List;" } == 1
    },
)

/** The holder of the views of the message composer, which finds the camera button by its id. */
internal object ComposerViewsFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(resourceLiteral(ResourceType.ID, "row_thread_composer_button_camera")),
)

/** Colors the buttons of the message composer, the camera among them, with the colors Instagram made for the chat. */
internal object ComposerButtonColorsFingerprint : Fingerprint(
    classFingerprint = ComposerViewsFingerprint,
    returnType = "V",
    filters = listOf(methodCall(smali = "Landroid/graphics/drawable/Drawable;->setColorFilter(Landroid/graphics/ColorFilter;)V")),
    custom = { method, _ -> method.parameters.size == 1 },
)

/** The placeholder in the extension that receives the names of the fields. */
internal object ThemeFieldsExtensionFingerprint : Fingerprint(
    name = "themeFields",
    definingClass = EXTENSION_CLASS,
)

internal const val THEME_FIELDS_SENTINEL = "__piko_chat_theme_fields__"
