/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

internal const val REPLY_READ_EXTENSION = "$PATCHES_DESCRIPTOR/dm/MarkAsReadAfterReply;"

internal object DmMutationSuccessFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L"),
    filters = listOf(string("success"), string("Mutation Successfully Sent")),
)

internal object DmTextSendNameFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("send_text_message")),
)

internal object ReplyReadThreadIdFingerprint : Fingerprint(
    definingClass = REPLY_READ_EXTENSION,
    name = "getThreadId",
)

internal object ReplyReadSendReceiptFingerprint : Fingerprint(
    definingClass = REPLY_READ_EXTENSION,
    name = "sendReceipt",
)
