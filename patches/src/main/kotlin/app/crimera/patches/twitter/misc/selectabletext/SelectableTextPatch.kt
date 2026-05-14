/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.selectabletext

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val selectableTextResourcePatch =
    resourcePatch {
        execute {
            val ids =
                listOf(
                    "user_name",
                    "user_bio",
                ).map { "@id/$it" }

            document("res/layout/profile_details.xml").use { editor ->
                val texts = editor.getElementsByTagName("com.twitter.ui.components.text.legacy.TypefacesTextView")
                for (i in 0 until texts.length) {
                    val text = texts.item(i) as Element
                    val id = text.getAttribute("android:id")

                    if (ids.contains(id)) {
                        text.setAttribute("android:textIsSelectable", "true")
                    }
                }
            }
        }
    }

@Suppress("unused")
val selectableTextPatch =
    bytecodePatch(
        name = "Selectable Text",
        description = "Makes bio and username selectable",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, selectableTextResourcePatch)

        execute {
            enableSettings("selectableText")
        }
    }
