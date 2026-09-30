/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.categories

import app.morphe.patcher.Fingerprint

// The inbox chat long-press menu builder; only the parameters with real class names are matched.
internal object ThreadLongPressMenuFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("DirectInboxThreadDialogController", "set_reminder_impression"),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 17 &&
            methodDef.parameters[1].type == "Landroid/view/View;" &&
            methodDef.parameters[8].type == "Lcom/instagram/model/direct/DirectThreadKey;"
    },
)

// The inbox adapter class, found by the error it throws for duplicate list items.
internal object InboxAdapterFingerprint : Fingerprint(
    strings = listOf("Seen duplicate model key for class "),
)

// The adapter's update method: every list Instagram shows goes through it.
internal object InboxAdapterUpdateFingerprint : Fingerprint(
    classFingerprint = InboxAdapterFingerprint,
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 2 && methodDef.parameters.all { it.type.startsWith("L") }
    },
)
