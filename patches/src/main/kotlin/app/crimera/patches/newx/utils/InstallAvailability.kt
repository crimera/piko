package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.PatchBuilder
import app.morphe.patcher.patch.PatchContext

/**
 * Mounts retain the stock manifest registrations. Settings, downloader components and package
 * changes therefore require a standard install. Apply this to selectable patches: availability
 * is selection metadata and is not inherited from their dependencies.
 *
 * [default] must match the patch declaration so non-mount selection keeps its existing behavior.
 */
internal fun <C : PatchContext<*>> PatchBuilder<C>.unsupportedOnMount(default: Boolean = true) {
    availability { installer, _ ->
        when {
            installer == InstallerType.MOUNT -> PatchAvailability.UNAVAILABLE
            default -> PatchAvailability.ENABLED
            else -> PatchAvailability.DISABLED
        }
    }
}
