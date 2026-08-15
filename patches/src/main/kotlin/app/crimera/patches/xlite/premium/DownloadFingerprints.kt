package app.crimera.patches.xlite.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal object XLiteDownloadEventHandlerFingerprint : Fingerprint(
    strings =
        listOf(
            "download_video_to_offline",
            "load_highest_quality",
            "video_download",
        ),
)

/**
 * New X-Lite video-player handler, distinct from the URT timeline handler above.
 * It handles both VideoDownloadClicked and VideoAddToOfflineClicked. The download
 * branch's preserved model calls and control-flow shape identify the obfuscated handler;
 * direct SubscriptionsFeatures checks in that handler gate offline-video availability.
 */
internal object XLiteVideoTabDownloadHandlerFingerprint : Fingerprint(
    strings = listOf("subscriptions_watermarked_video_download_enabled"),
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

internal object XLitePremiumSubscriptionCheckerFingerprint : Fingerprint(
    definingClass = "Lcom/x/subscriptions/",
    parameters = listOf(),
    returnType = "Z",
    custom = { method, _ ->
        val instructions = method.implementation?.instructions
        instructions != null && instructions.count() in 3..6 && instructions.any { ins ->
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass?.startsWith("Lcom/x/subscriptions/") == true && ref.returnType == "Z"
        }
    },
)

internal object SubscriptionsFeaturesHasAnyPremiumFingerprint : Fingerprint(
    returnType = "Z",
    strings =
        listOf(
            "feature/premium_basic",
            "feature/premium_plus",
            "feature/twitter_blue_verified",
        ),
)

private object MediaContentVideoClassFingerprint : Fingerprint(
    name = "toString",
    strings = listOf("MediaContentVideo(mediaId="),
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
    name = "toString",
    strings = listOf("MediaContentGif(mediaId="),
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
    name = "toString",
    strings = listOf("MediaContentImage(mediaId="),
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
