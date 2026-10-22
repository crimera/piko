/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val FOLLOW_LIST_SUGGESTIONS = "FollowListFragment.ShouldFetchSuggestedUsers"
internal const val FOLLOW_REQUEST_SUGGESTIONS = "ARG_SHOW_SUGGESTED_USERS"

/**
 * A suggestion flag read from the fragment arguments, repeated for each read of [key].
 */
private fun suggestionFlagReads(key: String, reads: Int) = List(reads) {
    listOf(
        string(key),
        methodCall(
            definingClass = "Landroid/os/BaseBundle;",
            name = "getBoolean",
            returnType = "Z",
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    )
}.flatten()

internal object MutualFollowersResponseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings = listOf("suggested_users", "show_see_all_followers_button"),
    filters = listOf(
        methodCall(returnType = "Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/String;->hashCode()I", location = MatchAfterImmediately()),
    ),
)

internal object FollowListSuggestionsFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    filters = suggestionFlagReads(FOLLOW_LIST_SUGGESTIONS, 1),
)

internal object FollowRequestSuggestionsFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    strings = listOf("ARG_HIDE_APPROVE_BUTTON"),
    filters = suggestionFlagReads(FOLLOW_REQUEST_SUGGESTIONS, 2),
)

internal object FriendingCenterResponseFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("IGFriendingCenterQuery", "isTopCategoryFullyExpanded", "downranked_ids"),
)

internal object FriendingCenterResponseParserFingerprint : Fingerprint(
    classFingerprint = FriendingCenterResponseFingerprint,
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        // Category parser, which returns null for unknown categories.
        methodCall(parameters = listOf("Ljava/lang/String;"), returnType = "Ljava/lang/Integer;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
    ),
)

private const val LIST = "Ljava/util/List;"
private const val COLLECTION = "Ljava/util/AbstractCollection;"
private const val LIST_IS_EMPTY = "Ljava/util/List;->isEmpty()Z"

private val ACTIVITY_FEED_SECTIONS_PARAMETERS = listOf(
    "Landroid/content/Context;", "L", USER_SESSION_CLASS, "L", "L", "L", "L", "L",
    "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
    LIST, LIST, LIST, "Z", "Z",
)

internal object ActivityFeedSectionsFingerprint : Fingerprint(
    parameters = ACTIVITY_FEED_SECTIONS_PARAMETERS,
    strings = listOf("new_stories", "FOLLOW_REQUEST", "SUGGESTED_USERS"),
)

/**
 * Follow-back section: users list read, then its empty check and the section header.
 */
internal object ActivityFeedFollowBackUsersFingerprint : Fingerprint(
    classFingerprint = ActivityFeedSectionsFingerprint,
    parameters = ACTIVITY_FEED_SECTIONS_PARAMETERS,
    filters = listOf(
        fieldAccess(type = LIST, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(smali = LIST_IS_EMPTY, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        opcode(Opcode.CONST, location = MatchAfterImmediately()),
        methodCall(
            definingClass = "this",
            parameters = listOf(COLLECTION, "I"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Suggested users section, which is built by a separate method of the same class.
 */
internal object ActivityFeedDelegatedUsersFingerprint : Fingerprint(
    classFingerprint = ActivityFeedSectionsFingerprint,
    parameters = ACTIVITY_FEED_SECTIONS_PARAMETERS,
    filters = listOf(
        fieldAccess(type = LIST, opcode = Opcode.IGET_OBJECT),
        methodCall(smali = LIST_IS_EMPTY, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        methodCall(
            definingClass = "this",
            parameters = listOf(USER_SESSION_CLASS, "L", COLLECTION, LIST, "I", "Z", "Z"),
            location = MatchAfterWithin(10),
        ),
    ),
)

/**
 * Recommendations: the result list, its empty check, the section header,
 * the pinned follow request row and then the recommendation rows loop.
 */
internal object ActivityFeedRecommendationsFingerprint : Fingerprint(
    classFingerprint = ActivityFeedSectionsFingerprint,
    parameters = ACTIVITY_FEED_SECTIONS_PARAMETERS,
    filters = listOf(
        fieldAccess(type = "Ljava/lang/Object;", opcode = Opcode.IGET_OBJECT),
        checkCast("Ljava/lang/Iterable;", location = MatchAfterImmediately()),
        methodCall(smali = LIST_IS_EMPTY),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z"),
        opcode(Opcode.MOVE_OBJECT_FROM16, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/List;->iterator()Ljava/util/Iterator;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/Iterator;->hasNext()Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
)

/**
 * Recommendations result handled by a separate method of the same class.
 */
internal object ActivityFeedDelegatedRecommendationsFingerprint : Fingerprint(
    classFingerprint = ActivityFeedSectionsFingerprint,
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/Object;", COLLECTION, "I", "Z", "Z"),
    filters = listOf(
        fieldAccess(type = "Ljava/lang/Object;", opcode = Opcode.IGET_OBJECT),
        checkCast(LIST, location = MatchAfterImmediately()),
    ),
)

internal object ReelsHiddenItemsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Ljava/util/List;"),
    filters = listOf(
        string("android_purge_26_q2_HiddenClipsFilter_transform"),
        methodCall(smali = "Ljava/util/Iterator;->hasNext()Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/Iterator;->next()Ljava/lang/Object;", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
    ),
)

internal object ReelsSuggestedUsersExitFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        // Native suggested users classifier.
        methodCall(definingClass = "this", parameters = listOf("L"), returnType = "Z", opcode = Opcode.INVOKE_STATIC),
        string("android_purge_26_q3_ClipsSuggestedUsersItemDelegateImpl_onExitClipsViewer"),
    ),
)

internal object ReelsItemTypeFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        string("NETEGO_SUGGESTED_USERS"),
        string("SUGGESTED_USERS"),
    ),
)
