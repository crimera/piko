/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal object PopulateStoryTrayFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf(
        "L", "L", "L", "L", "Ljava/lang/String;",
        "Ljava/util/List;", "Ljava/util/List;", "Z", "Z",
    ),
    filters = listOf(string("ReelStore.maybeAddNewTrayReelResponseItemIntfs")),
    custom = { method, _ -> method.parameterTypes.take(4).contains("Lcom/instagram/user/model/User;") },
)
