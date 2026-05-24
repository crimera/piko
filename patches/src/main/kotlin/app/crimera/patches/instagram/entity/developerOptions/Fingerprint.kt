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

internal object ExperimentsGetMobileConfigSpecifier : Fingerprint(
    strings = listOf("ExperimentParameter", "Failed to get config key with specifier:%d"),
)
