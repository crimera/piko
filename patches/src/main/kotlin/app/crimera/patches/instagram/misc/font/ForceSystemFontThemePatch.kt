/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.font

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/*
 * Changes Instagram's default theme font from prism_sans
 * to the Android system sans-serif font.
 */
internal val forceSystemFontThemePatch =
    resourcePatch(
        name = "Use system font in theme",
        description = "Internal dependency patch for Instagram's default theme font.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            document("res/values/styles.xml").use { stylesDocument ->

                val styles =
                    stylesDocument.getElementsByTagName("style")

                var fontFamilyItem: Element? = null

                for (styleIndex in 0 until styles.length) {

                    val style =
                        styles.item(styleIndex) as? Element
                            ?: continue

                    if (
                        style.getAttribute("name") !=
                        "Base.Theme.Instagram"
                    ) {
                        continue
                    }

                    val items =
                        style.getElementsByTagName("item")

                    for (itemIndex in 0 until items.length) {

                        val item =
                            items.item(itemIndex) as? Element
                                ?: continue

                        if (
                            item.getAttribute("name") !=
                            "android:fontFamily"
                        ) {
                            continue
                        }

                        if (fontFamilyItem != null) {
                            throw PatchException(
                                "Found multiple Instagram theme font declarations."
                            )
                        }

                        fontFamilyItem = item
                    }
                }

                val item =
                    fontFamilyItem
                        ?: throw PatchException(
                            "Could not find Instagram's default theme font declaration."
                        )

                if (
                    item.textContent.trim() !=
                    "@font/prism_sans"
                ) {
                    throw PatchException(
                        "Instagram's default theme font has an unexpected value."
                    )
                }

                item.textContent = "sans-serif"
            }
        }
    }