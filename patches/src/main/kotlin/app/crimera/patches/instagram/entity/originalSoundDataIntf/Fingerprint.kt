/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.originalSoundDataIntf

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/OriginalSoundDataIntf;"

internal object GetUserDataExtension : Fingerprint(
    name = "getUserData",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioIdExtension : Fingerprint(
    name = "getAudioId",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioNameExtension : Fingerprint(
    name = "getAudioName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioUrlExtension : Fingerprint(
    name = "getAudioUrl",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object OriginalSoundMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    filters = listOf(
        string("audio_asset_id"),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        string("hide_remixing"),
        // User data, read after the hide remixing entry is added.
        methodCall(opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterWithin(1)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        string("original_audio_title"),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        string("progressive_download_url"),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
    ),
)
