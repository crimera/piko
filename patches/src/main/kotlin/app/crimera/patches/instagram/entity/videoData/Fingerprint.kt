/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.videoData

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/VideoData;"

internal object VideoHeightExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getHeight",
)

internal object VideoWidthExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getWidth",
)

internal object VideoCodecExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getCodec",
)

internal object VideoUrlExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getUrl",
)

internal object VideoVersionMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    parameters = listOf("/VideoVersionIntf;"),
    strings = listOf("fallback", "url_expiration_timestamp_us"),
    filters = listOf("height", "type", "url", "width").flatMap { key ->
        listOf(
            string(key),
            methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf(), location = MatchAfterImmediately()),
        )
    },
)
