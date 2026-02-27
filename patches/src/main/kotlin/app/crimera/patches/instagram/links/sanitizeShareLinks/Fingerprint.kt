package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val TARGET_STRING_ARRAY =  arrayOf(
    "permalink",
    "story_item_to_share_url",
    "profile_to_share_url",
    "live_to_share_url",
)
internal object PermalinkResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[0]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson") && methodDef.implementation?.instructions?.count { it.opcode == Opcode.CONST_STRING } == 1
    },
)

internal object StoryUrlResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[1]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object ProfileUrlResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[2]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object LiveUrlResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[3]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)