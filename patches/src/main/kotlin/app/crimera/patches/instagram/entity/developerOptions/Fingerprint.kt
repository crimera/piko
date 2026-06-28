/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/DeveloperOptions;"
internal const val ITEM_CLASS_DESCRIPTOR = "$ENTITY_CLASS/DeveloperOptionsItem;"

internal object GetUniversalIdHelperClassExtension : Fingerprint(
    name = "getUniversalIdHelperClass",
    definingClass = ITEM_CLASS_DESCRIPTOR,
)

internal object GetQuickExperimentHelperClassExtension : Fingerprint(
    name = "getQuickExperimentHelperClass",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetExperimentItemHelperClassExtension : Fingerprint(
    name = "getExperimentItemHelperClass",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAllExperimentsClassExtension : Fingerprint(
    name = "getAllExperiments",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object ExperimentsValueBuilderFingerprint : Fingerprint(
    strings = listOf("default[after mc dispose]", "default[before mc init]", "override", "server"),
)

// "Failed to get config key with specifier:%d" co-located in v426 (X/AUE.A01) but moved
// to a string-table lookup (LX/000;->A00(I)) in v433+ (X/35z.A02). "ExperimentParameter"
// remains the only stable const-string anchor and is unique to this abstract class in
// both classes10.dex (v426) and classes3.dex (v433+).
internal object ExperimentsGetMobileConfigSpecifier : Fingerprint(
    strings = listOf("ExperimentParameter"),
)
