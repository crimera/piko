package app.crimera.patches.xlite.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object XLiteDownloadEventHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/items/post/",
    filters = listOf(string("download_video_to_offline")),
)

/**
 * New X-Lite video-player handler, distinct from the URT timeline handler above.
 * It handles both VideoDownloadClicked and VideoAddToOfflineClicked. The download
 * branch's preserved model calls and control-flow shape identify the obfuscated handler;
 * direct SubscriptionsFeatures checks in that handler gate offline-video availability.
 */
internal object XLiteVideoTabDownloadHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/x/video/tab/",
    filters = listOf(string("subscriptions_watermarked_video_download_enabled")),
    parameters = listOf("L"),
    returnType = "V",
    custom = { method, _ ->
        val instructions = method.implementation?.instructions
        val hasSubscriptionCheck = instructions?.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass?.startsWith("Lcom/x/subscriptions/") == true && ref.returnType == "Z"
        } == true
        val hasMediaCheck = instructions?.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass?.startsWith("Lcom/x/models/") == true && ref.returnType == "Z" && ref.parameterTypes.isEmpty()
        } == true
        hasSubscriptionCheck && hasMediaCheck
    },
)

internal object SubscriptionsFeaturesHasAnyPremiumFingerprint : Fingerprint(
    definingClass = "Lcom/x/subscriptions/",
    returnType = "Z",
    strings =
        listOf(
            "feature/premium_basic",
            "feature/premium_plus",
            "feature/twitter_blue_verified",
        ),
)

private object MediaContentVideoClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentVideo(mediaId=")),
)

internal object MediaContentVideoIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentVideoClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, _ ->
        method.name != "equals" &&
            method.name != "hashCode" &&
            method.name != "toString"
    },
)

private object MediaContentGifClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentGif(mediaId=")),
)

internal object MediaContentGifIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentGifClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, _ ->
        method.name != "equals" &&
            method.name != "hashCode" &&
            method.name != "toString"
    },
)

private object MediaContentImageClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentImage(mediaId=")),
)

internal object MediaContentImageIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentImageClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, _ ->
        method.name != "equals" &&
            method.name != "hashCode" &&
            method.name != "toString"
    },
)
