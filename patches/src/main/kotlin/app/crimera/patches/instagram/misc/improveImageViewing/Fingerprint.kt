/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.utils.Constants.EXTENDED_IMAGE_URL_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object SetDPIMetricsFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        opcode(Opcode.FILLED_NEW_ARRAY),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("%sdpi; %sx%s", location = MatchAfterImmediately()),
    ),
)

internal object ReturnExtendedImageUrlFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = EXTENDED_IMAGE_URL_CLASS,
    parameters = listOf("Ljava/lang/Integer;", "Ljava/util/List;", "I"),
    filters = listOf(methodCall(smali = "$EXTENDED_IMAGE_URL_CLASS->getWidth()I")),
)

internal object ImageUrlByWidthFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Lcom/instagram/common/typedurl/ImageUrl;",
    parameters = listOf("Ljava/lang/Integer;", "Ljava/util/List;", "I"),
    filters = listOf(
        methodCall(
            parameters = listOf(
                "Ljava/lang/Integer;", "Ljava/util/List;",
                "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function1;", "I",
            ),
            returnType = "Ljava/lang/Object;",
        ),
    ),
)
