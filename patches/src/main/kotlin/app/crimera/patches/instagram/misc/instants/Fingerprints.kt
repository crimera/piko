/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * The app's per-instant item, which has the view modes of an instant.
 */
internal object InstantItemFingerprint : Fingerprint(
    name = "hashCode",
    strings = listOf("NUX_VIDEO", "ONCE", "PERMANENT", "REPLAYABLE"),
)

internal object InstantMediaItemFingerprint : Fingerprint(
    name = "hashCode",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = "this", type = MEDIA_CLASS_NAME, opcode = Opcode.IGET_OBJECT),
        string("NUX_VIDEO"),
    ),
)

/**
 * The per-instant item is built from that instant's Media, its first parameter. Hooking its constructor catches every
 * instant as it is created and hands the Media straight to the extension.
 */
internal fun instantItemConstructorFingerprint(itemType: String) = Fingerprint(
    definingClass = itemType,
    name = "<init>",
)
