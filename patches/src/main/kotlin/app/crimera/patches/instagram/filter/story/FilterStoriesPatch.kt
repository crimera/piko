/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.crimera.patches.instagram.entity.reelResponseItem.reelResponseItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

// Heavily based on @brosssh work.
// https://github.com/brosssh/instagram-morphe-patches-library/blob/dev/patch-library/src/main/kotlin/app/morphe/library/instagram/patches/FilterStoriesListPatch.kt

@Suppress("unused")
val filterStoriesPatch =
    bytecodePatch(
        name = "Filter stories",
        description = "Filter stories to hide based on different categories",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, reelResponseItemEntity, userDataEntity)
        execute {

            PopulateStoryTrayFingerprint.matchAll(1..1).single().method.apply {
                // The JSON parser is shared with profile highlights. Filter only the story tray
                // after decoding, including cached responses, tail loads and the Following tray.
                addInstructions(
                    0,
                    """
                    invoke-static/range {p5 .. p5}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filter(Ljava/util/List;)Ljava/util/List;
                    move-result-object p5
                    """.trimIndent(),
                )
                enableSettings("storyFilters")
            }
        }
    }
