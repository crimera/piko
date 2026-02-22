package app.crimera.patches.instagram.misc.developerOption

import app.morphe.patcher.Fingerprint

internal object PromoteActivityOnCreate : Fingerprint(
    definingClass = "Lcom/instagram/business/promote/activity/PromoteActivity;",
    strings = listOf("selected 2 media for A/B testing"),
)
