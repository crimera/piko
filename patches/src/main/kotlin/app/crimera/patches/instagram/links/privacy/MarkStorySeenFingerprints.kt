/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val REEL_ITEM_CLASS = "Lcom/instagram/model/reels/ReelItem;"
internal const val MEDIA_CLASS = "Lcom/instagram/feed/media/Media;"
internal const val VIEW_CLASS = "Landroid/view/View;"
internal const val STRING_CLASS = "Ljava/lang/String;"
internal const val STORY_HEADER_BIND_TRACE = "ReelViewerItemBinder.bindHeaderViews"
internal const val STORY_SEEN_BRIDGE_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/story/StorySeenBridge;"
internal const val STORY_SEEN_BUTTON_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/story/StorySeenButton;"
internal const val STORY_SEEN_SCOPE_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/story/StorySeenRequestScope;"

internal object StorySeenUriBuilderFingerprint : Fingerprint(
    strings = listOf("media/seen/?reel=%s&live_vod=0"),
)

internal object ConsumedStoryFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("itas-android"),
    custom = { methodDef, _ ->
        methodDef.parameterTypes.size == 2 &&
            methodDef.parameterTypes.first() == REEL_ITEM_CLASS &&
            methodDef.hasLiteral(-414294270L)
    },
)

private fun Method.callsStoryConsumptionCallback(): Boolean =
    implementation?.instructions?.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        reference.returnType == "V" &&
            reference.parameterTypes.size == 3 &&
            reference.parameterTypes.first() == REEL_ITEM_CLASS &&
            reference.parameterTypes.last() == "Z"
    } == true

private fun Method.updatesSegmentedStoryProgress(): Boolean =
    implementation?.instructions?.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        reference.definingClass ==
            "Lcom/instagram/ui/widget/segmentedprogressbar/SegmentedProgressBar;" &&
            reference.name == "setProgress" &&
            reference.parameterTypes == listOf("F") &&
            reference.returnType == "V"
    } == true

internal fun Method.hasString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == value
    } == true

private fun Method.isStoryProgressCallback(): Boolean =
    returnType == "V" &&
        parameterTypes.size == 2 &&
        parameterTypes.last() == "I" &&
        callsStoryConsumptionCallback()

internal object PromptStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("model", "sticker"),
    custom = { methodDef, _ ->
        methodDef.isStoryProgressCallback() && methodDef.updatesSegmentedStoryProgress()
    },
)

internal object StandardStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.isStoryProgressCallback() &&
            methodDef.updatesSegmentedStoryProgress() &&
            !methodDef.hasString("model")
    },
)

internal object CompactStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.isStoryProgressCallback() &&
            methodDef.implementation!!.instructions.count() <= 20
    },
)

internal object StoryHeaderBindingFingerprint : Fingerprint(
    strings = listOf(STORY_HEADER_BIND_TRACE),
)

internal object ReelTypeFingerprint : Fingerprint(
    strings = listOf("ADS_REEL", "HIGHLIGHT_REEL", "NETEGO_REEL"),
)

internal object ExecutorSchedulerFingerprint : Fingerprint(
    strings = listOf(" ExecutorScheduler::scheduleOnExecutor()"),
)

private fun storySeenBridgeFingerprint(name: String) = Fingerprint(
    definingClass = STORY_SEEN_BRIDGE_DESCRIPTOR,
    name = name,
)

internal val IsSelfExtensionFingerprint = storySeenBridgeFingerprint("isSelfNative")
internal val PromptStoryProgressExtensionFingerprint =
    storySeenBridgeFingerprint("captureFromPromptProgressNative")
internal val StandardStoryProgressExtensionFingerprint =
    storySeenBridgeFingerprint("captureFromStandardProgressNative")
internal val CompactStoryProgressExtensionFingerprint =
    storySeenBridgeFingerprint("captureFromCompactProgressNative")
internal val StoryHeaderBindingExtensionFingerprint =
    storySeenBridgeFingerprint("captureFromHeaderBindNative")
internal val IsSupportedExtensionFingerprint = storySeenBridgeFingerprint("isSupportedNative")
internal val StoryIdExtensionFingerprint = storySeenBridgeFingerprint("storyIdNative")
internal val BuildRequestExtensionFingerprint = storySeenBridgeFingerprint("buildRequestNative")
internal val MarkLocalExtensionFingerprint = storySeenBridgeFingerprint("markLocalNative")
internal val ScheduleRequestExtensionFingerprint = storySeenBridgeFingerprint("scheduleRequestNative")
internal val RequestSucceededExtensionFingerprint = storySeenBridgeFingerprint("requestSucceededNative")

private fun Method.hasLiteral(value: Long): Boolean =
    implementation?.instructions?.any {
        (it as? WideLiteralInstruction)?.wideLiteral == value
    } == true

internal fun MethodReference.matches(other: MethodReference): Boolean =
    definingClass == other.definingClass &&
        name == other.name &&
        parameterTypes == other.parameterTypes &&
        returnType == other.returnType

internal fun FieldReference.matches(other: FieldReference): Boolean =
    definingClass == other.definingClass &&
        name == other.name &&
        type == other.type

internal fun uniqueMethodReference(
    method: MutableMethod,
    description: String,
    predicate: (MethodReference) -> Boolean,
): MethodReference {
    val matches =
        method.instructions.mapNotNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? MethodReference
        }.filter(predicate).distinctBy {
            listOf(it.definingClass, it.name, it.parameterTypes, it.returnType)
        }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one $description, found ${matches.size}")
}
