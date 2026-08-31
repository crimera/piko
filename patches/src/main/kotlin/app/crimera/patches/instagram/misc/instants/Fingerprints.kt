/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.morphe.patcher.Fingerprint

/** Instants are "quicksnap" internally, and this repository keeps its real name through R8. */
internal const val QUICK_SNAP_REPOSITORY_CLASS =
    "Lcom/instagram/quicksnap/data/repository/QuickSnapRepository;"

/**
 * Types the quicksnap classes touch. Pinned by instantsDownloadPatch before the fingerprint below
 * resolves, since a Media-carrying item on its own is not unique to instants.
 */
internal var quickSnapTypes: Set<String> = emptySet()

/**
 * The app's per-instant item, built from that instant's Media. Hooking its constructor catches every
 * instant as it is created and hands the Media straight to the extension.
 */
internal object InstantItemConstructorFingerprint : Fingerprint(
    name = "<init>",
    custom = { methodDef, classDef ->
        classDef.type in quickSnapTypes &&
            methodDef.parameters.firstOrNull()?.type == MEDIA_CLASS_NAME &&
            classDef.fields.any { it.type == MEDIA_CLASS_NAME }
    },
)
