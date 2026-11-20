/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

private const val SET_STORY_PROGRESS =
    "Lcom/instagram/ui/widget/segmentedprogressbar/SegmentedProgressBar;->setProgress(F)V"

/**
 * Builds the request of the pending seen stories.
 */
internal object StorySeenUriBuilderFingerprint : Fingerprint(
    parameters = listOf(USER_SESSION_CLASS),
    filters = listOf(
        string("media/seen/?reel=%s&live_vod=0"),
        // Seen stories payload.
        string("reels"),
        methodCall(parameters = listOf(STRING_CLASS, STRING_CLASS), returnType = "V", location = MatchAfterImmediately()),
        // Request.
        methodCall(parameters = listOf()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
    ),
)

/**
 * Story request run method, where the completed request result is read.
 */
internal fun storySeenRequestRunFingerprint(requestType: String) = Fingerprint(
    definingClass = requestType,
    name = "run",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        // Request task.
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        methodCall(smali = "Ljava/lang/Runnable;->run()V", location = MatchAfterImmediately()),
        // Completed task result.
        methodCall(parameters = listOf(), returnType = "Ljava/lang/Object;", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        // Successful result.
        opcode(Opcode.INSTANCE_OF, location = MatchAfterImmediately()),
    ),
)

/**
 * Schedules the request of the pending seen stories.
 */
internal fun storySeenScheduleFingerprint(pendingSeenClass: String, requestBuilder: MethodReference) = Fingerprint(
    returnType = "V",
    parameters = listOf(pendingSeenClass),
    filters = listOf(
        // Request manager.
        methodCall(parameters = listOf(USER_SESSION_CLASS), returnType = "L", opcode = Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(reference = requestBuilder, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // Schedule the request.
        methodCall(parameters = listOf("L"), returnType = "V", opcode = Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
    ),
)

/**
 * Marks the selected story as seen.
 */
internal fun consumedStoryFingerprint(pendingSeenClass: String) = Fingerprint(
    returnType = "V",
    parameters = listOf(REEL_ITEM_CLASS, "L"),
    filters = listOf(
        // 0: Excluded item predicate, inverted.
        methodCall(definingClass = REEL_ITEM_CLASS, parameters = listOf(), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.XOR_INT_LIT8, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // 4: Reel model of the reel.
        methodCall(parameters = listOf(USER_SESSION_CLASS, REEL_ITEM_CLASS), returnType = "I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        // 7: Regular story predicate.
        methodCall(definingClass = REEL_ITEM_CLASS, parameters = listOf(), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // 10: Excluded reel predicate.
        methodCall(parameters = listOf(), returnType = "Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        // 13: Reel owner id.
        fieldAccess(type = STRING_CLASS, opcode = Opcode.IGET_OBJECT),
        // 14: Story media.
        fieldAccess(
            definingClass = REEL_ITEM_CLASS,
            type = MEDIA_CLASS,
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterImmediately(),
        ),
        // 15: Story media conversion with the native caller id.
        opcode(Opcode.CONST),
        string("itas-android", location = MatchAfterImmediately()),
        methodCall(definingClass = MEDIA_CLASS, parameters = listOf(STRING_CLASS, "I"), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // 19: Adds the story to the pending seen stories.
        methodCall(
            definingClass = pendingSeenClass,
            parameters = listOf("L", USER_SESSION_CLASS, pendingSeenClass, STRING_CLASS),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
        ),
        // 20: Local seen state, updated with the item seen timestamp.
        methodCall(definingClass = REEL_ITEM_CLASS, parameters = listOf(), returnType = "J"),
        opcode(Opcode.MOVE_RESULT_WIDE, location = MatchAfterImmediately()),
        methodCall(parameters = listOf(USER_SESSION_CLASS, "J"), returnType = "V", location = MatchAfterImmediately()),
    ),
)

// Story header binder parameters.
internal const val STORY_HEADER_SESSION_PARAMETER = 1
internal const val STORY_HEADER_ITEM_PARAMETER = 2
internal const val STORY_HEADER_REEL_PARAMETER = 3
internal const val STORY_HEADER_HOLDER_PARAMETER = 8

internal object StoryHeaderBindingFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", USER_SESSION_CLASS, REEL_ITEM_CLASS, "L", "L", "L", "L", "L", "L", "Z"),
    strings = listOf(STORY_HEADER_BIND_TRACE),
)

internal object StoryToolbarBindingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", USER_SESSION_CLASS, REEL_ITEM_CLASS, "L", "L", "L", "L", "L", "L", "I", "Z"),
    filters = listOf(string("ReelViewerItemBinder.bindToolbarViews")),
)

/**
 * Root view of a story view holder, stored by its constructor.
 */
internal fun constructorRootViewFingerprint(classType: String) = Fingerprint(
    definingClass = classType,
    name = "<init>",
    filters = listOf(fieldAccess(definingClass = "this", type = VIEW_CLASS, opcode = Opcode.IPUT_OBJECT)),
)

internal fun storyHolderFactoryFingerprint(holderType: String) = Fingerprint(
    returnType = "Landroid/view/ViewGroup;",
    filters = listOf(
        newInstance(holderType),
        methodCall(smali = "Ljava/lang/Object;-><init>()V", location = MatchAfterImmediately()),
        fieldAccess(definingClass = holderType, type = VIEW_CLASS, opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object PromptStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        // Story consumption callback.
        methodCall(parameters = listOf(REEL_ITEM_CLASS, "L", "Z"), returnType = "V"),
        string("model"),
        string("sticker"),
        methodCall(smali = SET_STORY_PROGRESS),
    ),
)

internal object StandardStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        methodCall(smali = SET_STORY_PROGRESS),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
        // Story consumption is handled by a private helper.
        methodCall(
            definingClass = "this",
            parameters = listOf("L"),
            returnType = "V",
            opcode = Opcode.INVOKE_DIRECT,
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object DelegatedStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        methodCall(definingClass = "this", parameters = listOf("F"), returnType = "V", opcode = Opcode.INVOKE_DIRECT),
        // The segmented progress update is delegated to a separate holder.
        methodCall(parameters = listOf("L", "L"), returnType = "V", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
        methodCall(parameters = listOf(REEL_ITEM_CLASS, "L", "Z"), returnType = "V", opcode = Opcode.INVOKE_INTERFACE),
        methodCall(definingClass = "this", parameters = listOf("L"), returnType = "V", opcode = Opcode.INVOKE_DIRECT),
    ),
)

internal object CompactStoryProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        fieldAccess(definingClass = "this", type = REEL_ITEM_CLASS, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NE, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
        // Story consumption callback.
        methodCall(
            parameters = listOf(REEL_ITEM_CLASS, "L", "Z"),
            returnType = "V",
            opcode = Opcode.INVOKE_INTERFACE,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

internal object ReelTypeFingerprint : Fingerprint(
    strings = listOf("ReelType_unspecified", "ADS_REEL", "HIGHLIGHT_REEL", "NETEGO_REEL"),
)

internal object ExecutorSchedulerFingerprint : Fingerprint(
    filters = listOf(
        string(" ExecutorScheduler::scheduleOnExecutor()"),
        // Request wrapper.
        newInstance("L"),
    ),
)

/**
 * Executor request wrapper run method, which runs the wrapped request.
 */
internal fun executorWrapperRunFingerprint(wrapperType: String, requestInterface: String) = Fingerprint(
    definingClass = wrapperType,
    name = "run",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(definingClass = "this", type = requestInterface, opcode = Opcode.IGET_OBJECT),
        methodCall(definingClass = requestInterface, name = "run", parameters = listOf(), returnType = "V"),
        // Catch all handler of the request run.
        opcode(Opcode.MOVE_EXCEPTION),
    ),
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
