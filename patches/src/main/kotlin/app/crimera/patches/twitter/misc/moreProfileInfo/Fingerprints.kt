/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.moreProfileInfo

import app.morphe.patcher.Fingerprint
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral

internal object ProfileStatViewFormerFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/app/profiles/header/components",
    filters =
        listOf(
            resourceLiteral(ResourceType.ID, "stats_container"),
        ),
)

internal object ProfileStatViewLoaderFingerprint : Fingerprint(
    definingClass = "Landroidx/compose/foundation/text/input/internal/",
    filters =
        listOf(
            resourceLiteral(ResourceType.PLURALS, "profile_follower_count"),
        ),
)

internal object CheckIranFlagOnUserHeaderTextFieldsFingerprint : Fingerprint(
    strings = listOf("android_twemoji_iran_flag_emoji_enabled"),
    parameters = listOf("Landroid/widget/TextView;", "Ljava/lang/CharSequence;"),
)

internal object SetUserNameOnUserHeaderFingerprint : Fingerprint(
    classFingerprint = CheckIranFlagOnUserHeaderTextFieldsFingerprint,
    parameters = listOf("Ljava/lang/String;"),
)

internal object SetTweetStatViewValueExtension : Fingerprint(
    name = "setTweetStatViewValue",
)

internal object HeaderComponentViewFieldNameExtension : Fingerprint(
    name = "headerComponentViewFieldName",
)

internal object HeaderComponentContextFieldNameExtension : Fingerprint(
    name = "headerComponentContextFieldName",
)
