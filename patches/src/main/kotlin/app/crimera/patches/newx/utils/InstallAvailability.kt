package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.PatchBuilder
import app.morphe.patcher.patch.PatchContext

/**
 * Selection metadata for patches that work on every install type.
 *
 * Mounts retain the stock manifest registrations. Settings, the downloader folder picker and the
 * crash log receiver are therefore routed through registered stock components at runtime (see
 * `MountComponentProxyPatch`), so these patches stay selectable on a root mount. Availability is
 * selection metadata and is not inherited from dependencies, so selectable patches keep calling this.
 *
 * [default] must match the patch declaration so selection keeps its existing behavior.
 */
internal fun <C : PatchContext<*>> PatchBuilder<C>.unsupportedOnMount(default: Boolean = true) {
    availability { _, _ ->
        if (default) PatchAvailability.ENABLED else PatchAvailability.DISABLED
    }
}

/**
 * For patches that change the package name. A mount bind-mounts the APK over the installed
 * package, so it cannot be applied under another package name.
 */
internal fun <C : PatchContext<*>> PatchBuilder<C>.requiresPackageChange(default: Boolean = false) {
    availability { installer, _ ->
        when {
            installer == InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            default -> PatchAvailability.ENABLED
            else -> PatchAvailability.DISABLED
        }
    }
}
