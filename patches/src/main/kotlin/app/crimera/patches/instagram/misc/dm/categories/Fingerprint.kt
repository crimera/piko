/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.categories

import app.morphe.patcher.Fingerprint

// The inbox chat long-press menu builder.
internal object ThreadLongPressMenuFingerprint : Fingerprint(
    strings = listOf("DirectInboxThreadDialogController", "set_reminder_impression"),
    parameters = listOf(
        "Landroid/graphics/RectF;",
        "Landroid/view/View;",
        "LX/077r;",
        "LX/08r4;",
        "LX/0QAe;",
        "LX/095y;",
        "LX/0Qas;",
        "Lcom/instagram/model/direct/DirectShareTarget;",
        "Lcom/instagram/model/direct/DirectThreadKey;",
        "LX/03sn;",
        "Ljava/lang/Integer;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;",
        "Z",
        "Z",
        "Z",
    ),
    returnType = "V",
)

// The inbox adapter's update method: every list Instagram shows goes through it.
internal object InboxAdapterUpdateFingerprint : Fingerprint(
    definingClass = "LX/018n;",
    name = "A0e",
    returnType = "V",
    parameters = listOf("LX/01MK;", "LX/0Gzv;"),
)
