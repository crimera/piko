/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.performance

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Patch to maximize fluidity and reduce CPU/RAM churn in Stories, Reels, and Feed:
 *
 * 1. Disables DSP (Demand-Side Platform) Point context calculations.
 *    Instagram runs continuous geometry and viewability checks during scrolling on the UI thread,
 *    causing micro-stutters and frame drops. Short-circuiting these calculations frees up the main thread.
 *
 * 2. Forces automatic downscaling of large bitmaps in IgImageView (getScaleDownLargeBitmaps -> true).
 *    Decodes images at dimensions appropriate for the screen/view container rather than unconstrained
 *    raw dimensions, reducing memory usage and eliminating Garbage Collection (GC) pauses.
 *
 * 3. Disables forced image tracking overhead (getForceTracking -> false).
 *
 * 4. Suppresses ImageBlackBoxLogger callbacks invoked during image decode and rendering passes.
 */
@Suppress("unused")
val performanceOptimizationsPatch =
    bytecodePatch(
        name = "Performance and smoothness optimizations",
        description = "Improves scroll smoothness and reduces memory/CPU spikes in Stories, Reels, and Feed by disabling DSP scroll profiling, enabling automatic bitmap downscaling, and stripping image tracking overhead.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // 1. Disable continuous DSP context calculation during scroll passes
            mutableClassDefByOrNull("Lcom/facebook/analytics/dsp/point/DspPointContextHelper;")?.let { cls ->
                cls.methods.filter { it.implementation != null }.forEach { method ->
                    when (method.name) {
                        "A02" ->
                            method.addInstructions(
                                0,
                                """
                                const/4 v0, 0x0
                                return-object v0
                                """.trimIndent(),
                            )
                        "A00", "A01" ->
                            method.addInstructions(
                                0,
                                """
                                const/4 v0, 0x0
                                return v0
                                """.trimIndent(),
                            )
                    }
                }
            }

            mutableClassDefByOrNull("Lcom/facebook/analytics/dsp/point/IgPointContextProvider;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "A00" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return-object v0
                    """.trimIndent(),
                )
            }

            // 2. IgImageView optimizations: auto-downscale and disable force-tracking
            mutableClassDefByOrNull("Lcom/instagram/common/ui/widget/imageview/IgImageView;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "getScaleDownLargeBitmaps" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x1
                    return v0
                    """.trimIndent(),
                )

                cls.methods.find { it.implementation != null && it.name == "getForceTracking" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                )
            }

            // 3. Suppress ImageBlackBoxLogger callbacks during decoding and rendering
            mutableClassDefByOrNull("Lcom/instagram/analytics/ppr/ImageBlackBoxLogger;")?.let { cls ->
                val targetMethods = setOf("FQL", "FQX", "GIf", "GIi")
                cls.methods.filter { it.implementation != null && it.name in targetMethods }.forEach { method ->
                    method.addInstructions(0, "return-void")
                }
            }
        }
    }
