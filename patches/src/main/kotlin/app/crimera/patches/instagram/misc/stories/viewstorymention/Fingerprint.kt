package app.crimera.patches.instagram.misc.stories.viewstorymention

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${PATCHES_DESCRIPTOR}/story/ViewStoryMentionsPatch;"

internal object GetMediaObjectFromReelItemExtensionFingerprint:Fingerprint(
    custom = {method, classDef ->
        method.name == "getMediaObjectFromReelItem" && classDef.type == EXTENSION_CLASS_DESCRIPTOR
    }
)
