/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.feedWhitelist

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val IG_FRAGMENT_ACTIVITY_CLASS = "Lcom/instagram/base/activity/IgFragmentActivity;"

/**
 * Home feed response parser, which names the home feed item type.
 */
internal object MainFeedResponseParseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings = listOf("pagination_source", "is_initial_split_head_load_request", "more_available", "next_max_id"),
    filters = listOf(
        string("feed_items"),
        // Parses one feed item.
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L")),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/util/List;"),
    ),
)

/**
 * Adds an item to a list store. The home feed store, and so its cached and fetched posts, goes
 * through here.
 */
internal object ListStoreInsertFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "Ljava/lang/Object;", "I", "Z"),
    filters = listOf(
        // Item key.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("Ljava/lang/Object;"), returnType = "Ljava/lang/String;"),
        methodCall(smali = "Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;"),
        methodCall(smali = "Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        methodCall(smali = "Ljava/util/AbstractList;->add(ILjava/lang/Object;)V"),
    ),
    custom = { method, _ -> method.parameterTypes.first() == method.definingClass },
)

/**
 * Home feed adapter models, which read the home feed store.
 */
internal object MainFeedStoreFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("MainfeedAdapter.buildModels"),
        string("updateHelper"),
        opcode(Opcode.CHECK_CAST),
    ),
)

/**
 * Builds the generic user list request also used for the Following list.
 */
internal object UserListRequestFingerprint : Fingerprint(
    parameters = listOf(
        USER_SESSION_CLASS,
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
        "I", "Z", "Z", "Z", "Z",
    ),
    strings = listOf("search_surface", "max_id", "rank_token", "filter_dismissed_users", "enable_groups"),
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        // Response parser.
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "L", USER_SESSION_CLASS),
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Profile request that is run synchronously: sets a callback, then runs the request inline.
 */
internal object InlineProfileRequestFingerprint : Fingerprint(
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/String;"),
    returnType = USER_MODEL_CLASS_NAME,
    filters = listOf(
        string("users/%s/info/"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("L"), returnType = "V"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * Request completion, which hands the response to the callback's success method.
 */
internal fun requestFinishFingerprint(requestClass: String, callbackClass: String) = Fingerprint(
    definingClass = requestClass,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("onFinish"),
        string("onSuccess"),
        methodCall(
            definingClass = callbackClass,
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V",
        ),
    ),
)

/**
 * Next page cursor getter of the Pando user list response.
 */
internal fun userListNextMaxIdFingerprint(responseInterface: String) = Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(literal("next_max_id".hashCode())),
    custom = { _, classDef -> responseInterface in classDef.interfaces },
)

/**
 * Session getter of Instagram activities.
 */
internal object ModalActivitySessionFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/modal/ModalActivity;",
    name = "onDestroy",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = IG_FRAGMENT_ACTIVITY_CLASS,
            parameters = listOf(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        instanceOf(USER_SESSION_CLASS, location = MatchAfterImmediately()),
    ),
)
