package app.crimera.patches.newx.utils

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.utils.Constants.FEATURE_SWITCH_STORE_DESCRIPTOR
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import java.util.WeakHashMap

private const val REGISTER_PATCH_OVERRIDE_DESCRIPTOR =
    "$FEATURE_SWITCH_STORE_DESCRIPTOR->registerPatchOverride(Ljava/lang/String;Z)V"

private object NewXFeatureFlagPatchState {
    private val overrides = WeakHashMap<BytecodePatchContext, MutableMap<String, Boolean>>()

    @Synchronized
    fun register(
        context: BytecodePatchContext,
        flagName: String,
        enabled: Boolean,
    ): Boolean {
        val contextOverrides = overrides.getOrPut(context) { mutableMapOf() }
        if (flagName in contextOverrides) {
            val existing = contextOverrides.getValue(flagName)
            if (existing != enabled) {
                throw PatchException(
                    "Conflicting NewX feature flag values for '$flagName': $existing and $enabled",
                )
            }
            return false
        }

        contextOverrides[flagName] = enabled
        return true
    }
}

/**
 * Forces a Boolean NewX feature switch to the requested value.
 *
 * The calling patch must depend on `featureFlagPatch`, which installs the feature-switch accessors:
 *
 * ```kotlin
 * dependsOn(featureFlagPatch)
 * execute { setNewXFeatureFlag("x_home_nav_enabled", enabled = false) }
 * ```
 *
 * A user's persisted override still takes precedence over this patch-provided default.
 */
context(patchContext: BytecodePatchContext)
fun setNewXFeatureFlag(
    flagName: String,
    enabled: Boolean,
) {
    if (flagName.isBlank()) throw PatchException("NewX feature flag name must not be blank")
    if (!NewXFeatureFlagPatchState.register(patchContext, flagName, enabled)) return

    newXInitHook.fingerprint.method.registerNewXFeatureFlag(flagName, enabled)
}

internal fun MutableMethod.registerNewXFeatureFlag(
    flagName: String,
    enabled: Boolean,
) {
    insertHook(
        index = 0,
        // Register once on Application.onCreate entry; internal edges must retain their original targets.
        relocateBranchTargets = false,
    ) {
        val flagNameRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val enabledRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        constString(flagNameRegister, flagName)
        constInt(enabledRegister, if (enabled) 1 else 0)
        invokeStatic(
            methodReference(REGISTER_PATCH_OVERRIDE_DESCRIPTOR),
            flagNameRegister,
            enabledRegister,
        )
    }
}
