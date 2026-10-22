/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.utils.Constants.EXTENDED_IMAGE_URL_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

internal object SetDPIMetricsFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("%sdpi; %sx%s"),
    filters = listOf(
        methodCall(smali = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // Formatted DPI, height and width.
        opcode(Opcode.FILLED_NEW_ARRAY, location = MatchAfterWithin(2)),
    ),
)

internal object ReturnExtendedImageUrlFingerprint : Fingerprint(
    returnType = EXTENDED_IMAGE_URL_CLASS,
    parameters = listOf("Ljava/lang/Integer;", "Ljava/util/List;", "I"),
    filters = listOf(
        methodCall(smali = "$EXTENDED_IMAGE_URL_CLASS->getHeight()I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        methodCall(smali = "$EXTENDED_IMAGE_URL_CLASS->getWidth()I", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_NE, location = MatchAfterImmediately()),
    ),
)
