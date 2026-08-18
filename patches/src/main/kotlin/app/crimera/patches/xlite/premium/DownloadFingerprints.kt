package app.crimera.patches.xlite.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// ALPHA + BETA PATH: shared URT/Compose timeline media-action handler.
internal object XLiteDownloadEventHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/items/post/",
    filters = listOf(string("download_video_to_offline")),
)

/**
 * ALPHA-ONLY PATH: legacy and new video-tab handlers handle VideoDownloadClicked and
 * VideoAddToOfflineClicked. Beta removed this feature-switch-shaped callback; beta uses the
 * shared URT handler above plus the global offline gates below.
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

// ALPHA: e()Z. BETA: Q()Z. Both are the all-tier premium gate used by media saving.
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

/**
 * ALPHA: g()Z. BETA: M()Z. Two-tier premium gate used by offline-video and media-gallery
 * downloads; the exact obfuscated method name is deliberately resolved from its strings.
 */
internal object SubscriptionsFeaturesOfflinePremiumFingerprint : Fingerprint(
    definingClass = "Lcom/x/subscriptions/",
    returnType = "Z",
    parameters = emptyList(),
    filters =
        listOf(
            string("feature/twitter_blue_verified"),
            string("feature/premium_plus"),
        ),
    custom = { method, _ ->
        method.implementation?.instructions?.none { instruction ->
            instruction.getReference<StringReference>()?.string == "feature/premium_basic"
        } == true
    },
)

// ALPHA: i()Z. BETA: s()Z. Global feature flag used by every offline-video surface.
internal object SubscriptionsFeaturesOfflineVideoEnabledFingerprint : Fingerprint(
    definingClass = "Lcom/x/subscriptions/",
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(string("subscriptions_feature_offline_video")),
)

private const val DOWNLOADABLE_TEXT = ", isDownloadable="

// ALPHA fallback only: the model accessor is obfuscated, so derive its field from toString().
private fun downloadableField(classDef: ClassDef): FieldReference? {
    val toStringMethod =
        classDef.methods.singleOrNull { method ->
            method.name == "toString" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/String;"
        } ?: return null
    val instructions = toStringMethod.implementation?.instructions ?: return null
    val markerIndex = instructions.indexOfFirst { instruction ->
        instruction.getReference<StringReference>()?.string == DOWNLOADABLE_TEXT
    }
    if (markerIndex < 0) return null

    return instructions
        .drop(markerIndex + 1)
        .take(4)
        .firstOrNull { instruction -> instruction.opcode == Opcode.IGET_BOOLEAN }
        ?.getReference<FieldReference>()
}

private fun Method.readsField(field: FieldReference): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.opcode == Opcode.IGET_BOOLEAN &&
            instruction.getReference<FieldReference>()?.toString() == field.toString()
    } == true

/**
 * Alpha keeps the semantic downloadable property as an obfuscated override. The only
 * no-argument boolean method reading the field rendered next to `isDownloadable=` is the
 * property accessor; component methods were introduced by the beta model shape.
 */
private fun isLegacyDownloadableAccessor(
    method: Method,
    classDef: ClassDef,
): Boolean {
    if (!AccessFlags.FINAL.isSet(method.accessFlags)) return false
    val field = downloadableField(classDef) ?: return false
    val accessors =
        classDef.methods.filter { candidate ->
            candidate.returnType == "Z" &&
                candidate.parameterTypes.isEmpty() &&
                candidate.readsField(field)
        }
    return accessors.size == 1 && accessors.single().toString() == method.toString()
}

private object MediaContentVideoClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentVideo(mediaId=")),
)

// BETA: preserved isDownloadable(). ALPHA: structural obfuscated-accessor fallback.
internal object MediaContentVideoIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentVideoClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "isDownloadable" || isLegacyDownloadableAccessor(method, classDef)
    },
)

private object MediaContentGifClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentGif(mediaId=")),
)

// BETA: preserved isDownloadable(). ALPHA: structural obfuscated-accessor fallback.
internal object MediaContentGifIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentGifClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "isDownloadable" || isLegacyDownloadableAccessor(method, classDef)
    },
)

private object MediaContentImageClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    filters = listOf(string("MediaContentImage(mediaId=")),
)

// BETA: preserved isDownloadable(). ALPHA: structural obfuscated-accessor fallback.
internal object MediaContentImageIsDownloadableFingerprint : Fingerprint(
    classFingerprint = MediaContentImageClassFingerprint,
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        method.name == "isDownloadable" || isLegacyDownloadableAccessor(method, classDef)
    },
)
