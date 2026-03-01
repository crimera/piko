package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.userfriendshipstatus.EXTENSION_CLASS
import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/MediaData;"

internal object GetHelperClassExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getHelperClass"
)

internal object GetMentionSetExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMentionSet"
)

internal object ReelsMentionDoubleTapFingerprint: Fingerprint(
    returnType = "V",
    strings = listOf("userSession","direct_add_mention_tap")
)
