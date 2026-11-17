package app.crimera.patches.utils

import app.crimera.patches.common.ShapeFingerprint as SharedShapeFingerprint
import app.crimera.patches.common.scopedMatchAll as sharedScopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull as sharedScopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext

// Legacy Twitter callers keep their source contract while NewX uses the shared API directly.
internal typealias ShapeFingerprint = SharedShapeFingerprint

context(_: BytecodePatchContext)
internal fun Fingerprint.scopedMatchAllOrNull(): List<Match>? =
    this.sharedScopedMatchAllOrNull()

context(_: BytecodePatchContext)
internal fun Fingerprint.scopedMatchAll(): List<Match> =
    this.sharedScopedMatchAll()
