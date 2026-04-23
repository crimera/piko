/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/DeveloperOptions;"

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
