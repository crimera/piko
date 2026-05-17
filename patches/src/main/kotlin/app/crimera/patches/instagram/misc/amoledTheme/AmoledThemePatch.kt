/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.amoledTheme

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.findElementByAttributeValueOrThrow

@Suppress("unused")
val amoledThemePatch =
    resourcePatch(
        name = "Amoled theme",
        description = "Replaces Instagram's dark-mode background greys with pure black for AMOLED displays.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // ── res/values-night/colors.xml ──────────────────────────
            // Three elevated/secondary surface tokens normally resolve
            // to bds_grey_* in dark mode. Remap them to pure black so
            // cards, sheets, modals, and elevated surfaces match the
            // primary background. igds_elevated_separator is left as
            // bds_grey_8 so dividers remain visible.
            val nightOverrides =
                mapOf(
                    "igds_secondary_background" to "@color/bds_black",
                    "igds_elevated_background" to "@color/bds_black",
                    "igds_elevated_highlight_background" to "@color/bds_black",
                )

            document("res/values-night/colors.xml").use { document ->
                val colors = document.getElementsByTagName("color")
                nightOverrides.forEach { (name, value) ->
                    colors.findElementByAttributeValueOrThrow("name", name).textContent = value
                }
            }

            // ── res/values/colors.xml ────────────────────────────────
            // igds_prism_black is IG's "Prism" design-system black,
            // hardcoded to #ff0c1014 (a near-black with a slight blue
            // tint). It has no night-mode variant, so it renders the
            // same in both themes — and IG's modern feed/header/nav
            // surfaces use it directly, which is why the elevated
            // overrides above don't reach them. Force it to pure
            // black; in light mode it's used for text/icons where
            // pure black is also correct.
            val defaultOverrides =
                mapOf(
                    "igds_prism_black" to "#ff000000",
                )

            document("res/values/colors.xml").use { document ->
                val colors = document.getElementsByTagName("color")
                defaultOverrides.forEach { (name, value) ->
                    colors.findElementByAttributeValueOrThrow("name", name).textContent = value
                }
            }
        }
    }
