/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.autotranslate

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

// The extension still uses the frozen Legacy X 12.19.1 model/QuoteView stubs.
// Match rendering methods by their contracts and instructions, not obfuscated method names.
internal object CapturePostFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/ui/textcontent/",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Lcom/twitter/model/core/e;", "L", "L", "L", "Z", "Z"),
    filters = listOf(fieldAccess(opcode = Opcode.IGET_OBJECT, definingClass = "this", type = "Landroid/view/View;")),
)

internal object TimelineTextFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/ui/textcontent/",
    returnType = "V",
    parameters = listOf("Ljava/lang/CharSequence;"),
    filters = listOf(methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        definingClass = "Lcom/twitter/ui/widget/TextContentView;",
        name = "setTextWithVisibility",
        parameters = listOf("Ljava/lang/CharSequence;"),
        returnType = "V",
    )),
)

internal object FocalTextFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/focal/ui/textcontent/",
    returnType = "V",
    parameters = listOf("Ljava/lang/CharSequence;"),
    filters = listOf(methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        definingClass = "Landroid/widget/TextView;",
        name = "setText",
        parameters = listOf("Ljava/lang/CharSequence;", "Landroid/widget/TextView\$BufferType;"),
        returnType = "V",
    )),
)

internal object FocalAttachmentFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/focal/ui/textcontent/",
    name = "onViewAttachedToWindow",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        definingClass = "Landroid/widget/TextView;",
        name = "setText",
        parameters = listOf("Ljava/lang/CharSequence;", "Landroid/widget/TextView\$BufferType;"),
        returnType = "V",
    )),
)

internal object QuotedPostFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/QuoteView;",
    returnType = "V",
    parameters = listOf("Lcom/twitter/model/core/e;", "Lcom/twitter/ui/renderable/", "Ljava/lang/Integer;"),
    filters = listOf(fieldAccess(opcode = Opcode.IPUT_BOOLEAN, definingClass = "this", name = "Q", type = "Z")),
)
