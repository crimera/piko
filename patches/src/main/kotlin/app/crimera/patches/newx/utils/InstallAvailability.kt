package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.PatchBuilder
import app.morphe.patcher.patch.PatchContext

internal fun <C : PatchContext<*>> PatchBuilder<C>.unsupportedOnMount(default: Boolean = true) {
    availability { _, _ ->
        if (default) PatchAvailability.ENABLED else PatchAvailability.DISABLED
    }
}

internal fun <C : PatchContext<*>> PatchBuilder<C>.requiresPackageChange(default: Boolean = false) {
    availability { installer, _ ->
        when {
            installer == InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            default -> PatchAvailability.ENABLED
            else -> PatchAvailability.DISABLED
        }
    }
}
