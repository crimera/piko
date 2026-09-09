package app.crimera.patches.newx.premium

import app.crimera.patches.newx.models.fieldForToStringLabel
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
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
internal object NewXDownloadEventHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/items/post/",
    filters = listOf(string("download_video_to_offline")),
)

/**
 * ALPHA-ONLY PATH: legacy and new video-tab handlers handle VideoDownloadClicked and
 * VideoAddToOfflineClicked. Beta removed this feature-switch-shaped callback; beta uses the
 * shared URT handler above plus the global offline gates below.
 */
internal object NewXVideoTabDownloadHandlerFingerprint : Fingerprint(
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

// ALPHA fallback only: the model accessor is obfuscated, so derive its field from the
// semantic toString label. The shared resolver handles both direct and helper-based
// StringBuilder layouts and fails on an absent or ambiguous field.
private fun downloadableField(classDef: ClassDef): FieldReference {
    val toStringMethods =
        classDef.methods.filter { method ->
            method.name == "toString" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/String;"
        }
    if (toStringMethods.size != 1) {
        throw PatchException(
            "Expected exactly one NewX media-content toString() while resolving '$DOWNLOADABLE_TEXT' " +
                "in ${classDef.type}, found ${toStringMethods.size}: " +
                toStringMethods.joinToString { it.toString() }.ifEmpty { "<none>" },
        )
    }

    val field = toStringMethods.single().fieldForToStringLabel(DOWNLOADABLE_TEXT)
    if (field.definingClass != classDef.type || field.type != "Z") {
        throw PatchException(
            "NewX '$DOWNLOADABLE_TEXT' resolved to an unexpected field in ${classDef.type}; " +
                "expected local boolean, found $field",
        )
    }
    return field
}

private fun Method.booleanFieldsRead(): List<FieldReference> =
    implementation?.instructions
        ?.filter { instruction -> instruction.opcode == Opcode.IGET_BOOLEAN }
        ?.mapNotNull { instruction -> instruction.getReference<FieldReference>() }
        ?.distinctBy(FieldReference::toString)
        .orEmpty()

private fun Method.writesField(field: FieldReference): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.opcode == Opcode.IPUT_BOOLEAN &&
            instruction.getReference<FieldReference>()?.toString() == field.toString()
    } == true

private fun ClassDef.hasNamedDownloadableAccessor(): Boolean =
    methods.any { method ->
        method.name == "isDownloadable" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "Z"
    }

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
    // A preserved semantic accessor is already the preferred shape. Do not reinterpret a
    // generated component reader as a second legacy accessor in that shape.
    if (classDef.hasNamedDownloadableAccessor()) return false
    val methodFields = method.booleanFieldsRead()
    if (methodFields.size != 1) return false

    val field = downloadableField(classDef)
    if (methodFields.single().toString() != field.toString()) return false

    val accessors =
        classDef.methods.filter { candidate ->
            candidate.returnType == "Z" &&
                candidate.parameterTypes.isEmpty() &&
                candidate.booleanFieldsRead().singleOrNull()?.toString() == field.toString()
        }
    if (accessors.size != 1) {
        throw PatchException(
            "Expected exactly one NewX legacy downloadable accessor for $field in ${classDef.type}, " +
                "found ${accessors.size}: " +
                accessors.joinToString { it.toString() }.ifEmpty { "<none>" },
        )
    }

    val constructors = classDef.methods.filter { it.name == "<init>" }
    if (constructors.none { it.writesField(field) }) {
        throw PatchException(
            "NewX legacy downloadable field $field is not initialized by any constructor in ${classDef.type}; " +
                "constructors: " +
                constructors.joinToString { it.toString() }.ifEmpty { "<none>" },
        )
    }
    return accessors.single().toString() == method.toString()
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
