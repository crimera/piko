package crimera.patches.twitter.timeline.hidebookmarkintimeline.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object HideBookmarkInTimelineFingerprint1: MethodFingerprint(
    strings = listOf("bookmarks_in_timelines_enabled"),
    returnType = "Z"

)