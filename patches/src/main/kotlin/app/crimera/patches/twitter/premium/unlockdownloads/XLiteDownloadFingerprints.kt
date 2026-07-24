/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.unlockdownloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

internal object XLiteDownloadEventHandlerFingerprint : Fingerprint(
    strings =
        listOf(
            "download_video_to_offline",
            "load_highest_quality",
            "video_download",
        ),
)

/**
 * New X-Lite video-player handler, distinct from the URT timeline handler above.
 * It gates VideoDownloadClicked with an injected checker before isDownloadable().
 * The method/class names are obfuscated; the preserved model method and control-flow
 * shape are the stable anchors.
 */
internal object XLiteVideoTabDownloadHandlerFingerprint : Fingerprint(
    parameters = listOf("L"),
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                parameters = listOf(),
                returnType = "Z",
            ),
            opcode(
                opcode = Opcode.MOVE_RESULT,
                location = MatchAfterImmediately(),
            ),
            opcode(
                opcode = Opcode.IF_EQZ,
                location = MatchAfterImmediately(),
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "isDownloadable",
                parameters = listOf(),
                returnType = "Z",
            ),
            opcode(
                opcode = Opcode.MOVE_RESULT,
                location = MatchAfterImmediately(),
            ),
            opcode(
                opcode = Opcode.IF_EQZ,
                location = MatchAfterImmediately(),
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "getVariants",
                parameters = listOf(),
            ),
        ),
)

internal object XLitePremiumSubscriptionCheckerFingerprint : Fingerprint(
    parameters = listOf(),
    returnType = "Z",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = "this",
                type = "Lcom/x/subscriptions/SubscriptionsFeatures;",
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Lcom/x/subscriptions/SubscriptionsFeatures;",
                parameters = listOf(),
                returnType = "Z",
                location = MatchAfterImmediately(),
            ),
            opcode(
                opcode = Opcode.MOVE_RESULT,
                location = MatchAfterImmediately(),
            ),
            opcode(
                opcode = Opcode.RETURN,
                location = MatchAfterImmediately(),
            ),
        ),
)

internal object SubscriptionsFeaturesHasAnyPremiumFingerprint : Fingerprint(
    strings =
        listOf(
            "feature/twitter_blue",
            "feature/premium_basic",
            "feature/twitter_blue_verified",
            "feature/premium_plus",
        ),
)

internal object MediaContentVideoIsDownloadableFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/MediaContent\$MediaContentVideo;",
    name = "isDownloadable",
    returnType = "Z",
)

internal object MediaContentGifIsDownloadableFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/MediaContent\$MediaContentGif;",
    name = "isDownloadable",
    returnType = "Z",
)
