/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import com.android.tools.smali.dexlib2.AccessFlags

// This fingerprint is also used in MarkAsRead patch.
object DMSeenFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters =
        listOf(
            string("mark_thread_seen-"),
        ),
)

object InboxButtonFingerprint : Fingerprint(
    filters = listOf(resourceLiteral(ResourceType.ID, "action_bar_inbox_button")),
)

object CreateTabButtonFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    filters =
        listOf(
            string("InstagramMainActivity.createTabButton("),
        ),
)
