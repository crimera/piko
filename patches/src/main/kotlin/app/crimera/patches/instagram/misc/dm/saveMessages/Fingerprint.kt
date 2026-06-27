/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.morphe.patcher.Fingerprint

/**
 * Targets LX/0gL;.A00(LX/R0r;LX/9ZA;Ljava/lang/String;)Z in v426 classes.dex.
 *
 * This is the per-field JSON dispatch helper on the REST delivery path:
 *   REST: LX/0gL;.parseFromJson → LX/AtQ;.parse → LX/0gG;.unsafeParseFromJson
 *         → LX/0gL;.A00 (called once per JSON key)    ← this fingerprint locates A00
 *         Hook 1 is injected in parseFromJson, NOT in A00.
 *
 * IMPORTANT: LX/0gL; lives in classes.dex, NOT classes12.dex. The fingerprint
 * resolves to classes.dex (returnType=Z uniquely selects it there). classes12.dex
 * has 3 methods containing "hide_in_thread" but none with returnType=Z.
 *
 * This fingerprint is NOT hooked directly. It is used only to locate the
 * containing class LX/0gL;, from which parseFromJson is retrieved and hooked.
 *
 * v426 field mapping — all fields declared on LX/9ZA; (base class):
 *   item_id        → A13:Ljava/lang/String;
 *   hide_in_thread → A1Y:Z                           (true = unsent)
 *   user_id        → A1M:Ljava/lang/String;
 *   timestamp      → A1J:Ljava/lang/String;           (microseconds; divide by 1000 for ms)
 *   text           → A1I:Ljava/lang/String;
 *   item_type      → A0Y:LX/8ot;                     (enum — call toString())
 *   thread_key     → A0W:Lcom/instagram/model/direct/DirectThreadKey;
 *   DirectThreadKey.threadId → .A00:Ljava/lang/String;
 *   MSys delta ref → A0V:LX/02L;
 *
 * v408 fallback field names (wired in SavedMessagesHook for multi-version support):
 *   DirectItem: LX/5jI;, hideInThread: A2V:Z, item_id: getter A0l(), threadKey: A16/A18
 */
internal object DirectItemFieldParserFingerprint : Fingerprint(
    strings = listOf("item_id", "hide_in_thread"),
    returnType = "Z",
)

/**
 * Targets LX/0gF;.A0P(UserSession, LX/02L;)LX/0gF; in v426 classes12.dex.
 *
 * A0P is the MQTT/MSys post-processing step — the ONLY hook point for real-time
 * messages delivered via the MSys Modular Client Application sync framework.
 * It is NOT on the REST/JSON path; parseFromJson is never called for MQTT messages.
 *
 *   MQTT: MSys delta (LX/02L;) → LX/0gF;.A02(..., LX/02L;, ...)  [builds item]
 *         → LX/0gF;.A0P(UserSession, LX/02L;)                     ← Hook 2 here
 *
 * LX/0gF; (PUBLIC FINAL) extends LX/9ZA; (DirectItem base class) with NO additional
 * instance fields. The runtime type of `this` inside A0P is LX/0gF;, but all data
 * fields (item_id=A13, hide_in_thread=A1Y, etc.) are declared on LX/9ZA;.
 * onMessageReceived uses getFieldValue() which walks the superclass chain.
 *
 * A0P return structure (v426, offset in classes12.dex):
 *   success: return-object v17 (= this) at offsets 0351, 0352
 *   error:   return-object v2  (= null) at offsets 004a/004b, 02c8/02c9, 0326/0327
 *   Hook 2 is injected before the last RETURN_OBJECT (picks offset 0352, the this-return).
 *
 * Anchor strings are unique to A0P and present only in classes12.dex.
 * returnType omitted to avoid hardcoding the obfuscated LX/0gF; class name.
 */
internal object DirectItemPostprocessFingerprint : Fingerprint(
    strings = listOf("DirectMessage.postprocess.%s", "Encountered DirectMessage with null type"),
)

/**
 * Targets the SQLite DAO "delete/hide DirectItem by ID" method across versions:
 *   v14.70 (v408): LX/0LJ;.A0P(DirectThreadKey, String, String)V  (classes2.dex)
 *   v14.90 (v4xx): LX/1yN;.A0S(DirectThreadKey, String, String)V  (classes2.dex)
 *
 * When Instagram unsends a message it calls this DAO to remove it from the
 * local SQLite; at that point the item_id arrives as a DIRECT PARAMETER
 * (no obfuscated field name reflection needed).
 *
 * Hook 4 is injected at index 0 (method entry) so it fires before the actual
 * delete executes — our DB record (written by Hook 1/2 on arrival) is still
 * present and can be marked deleted.
 *
 * Anchor: "Both message ID and client context is null." is stable across
 * v408 → v426 → v4xx and appears only once in each version's dex set.
 *
 * Parameters:
 *   p1 = DirectThreadKey   (com.instagram.model.direct.DirectThreadKey)
 *   p2 = server_item_id    (String, nullable — the stable item ID from server)
 *   p3 = client_item_id    (String, nullable — local client-context fallback)
 */
internal object DirectItemDbHideFingerprint : Fingerprint(
    strings = listOf("Both message ID and client context is null."),
    parameters = listOf(
        "Lcom/instagram/model/direct/DirectThreadKey;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    returnType = "V",
)

/**
 * The DM action-bar builder (e.g. LX/2p9;->A01 on v426) — the same method dmActionBarButtonPatch
 * hooks. It constructs the open chat's DirectThreadKey, so Hook 5 reads the thread id from there
 * at patch time and records it (noteOpenThreadId), removing the need for a runtime object-graph
 * walk to find the open chat.
 */
internal object DMActionBarThreadFingerprint : Fingerprint(
    strings = listOf("threadClientInfra", "actionBarListener"),
    returnType = "V",
)

/**
 * The DM thread/inbox JSON deserializer's per-field dispatch helper
 * ({@code LX/6o9;.A00(LX/R0r;LX/6oB;Ljava/lang/String;)Z} on v426, classes3.dex) — the thread
 * analogue of {@code DirectItemFieldParserFingerprint}. When the JSON key is {@code "users"} it
 * parses the participant list into {@code List<com.instagram.user.model.User>} and stores it on the
 * thread object. Hook 6 finds that {@code "users"} branch and harvests each participant's
 * id -> @handle into the username directory as the inbox/thread loads — so a sender is named even
 * before any unsend, and the unsend notification shows a real name (not the numeric id).
 *
 * Anchors are thread-summary JSON keys that co-occur only in this dispatcher; returnType Z selects
 * the per-field helper (not parseFromJson, which returns the thread object).
 */
internal object ThreadUsersDispatchFingerprint : Fingerprint(
    strings = listOf("users", "admin_user_ids", "left_users", "thread_v2_id", "input_mode"),
    returnType = "Z",
)
