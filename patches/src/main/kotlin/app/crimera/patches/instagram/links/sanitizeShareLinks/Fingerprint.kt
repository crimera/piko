package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal val TARGET_STRING_ARRAY =  arrayOf(
    "permalink",
    "profile_to_share_url",
)
internal object PermalinkResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[0]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson") && methodDef.implementation?.instructions?.filter { it.opcode == Opcode.CONST_STRING }!!.size < 3
    },
)

internal object ProfileUrlResponseJsonParserFingerprint: Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[1]),
    custom = { methodDef, _->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object StoryUrlResponseImplFingerprint: Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/request/StoryItemUrlResponseImpl;",
)

internal object LiveUrlResponseImplFingerprint: Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/request/LiveItemLinkUrlResponseImpl;",
)