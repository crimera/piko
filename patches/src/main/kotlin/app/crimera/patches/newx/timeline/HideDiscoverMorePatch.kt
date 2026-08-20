package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideDiscoverMorePatch =
    bytecodePatch(
        name = "NewX: Hide Discover more",
        description = "Removes the Discover more module from post-detail timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineModelAdapterPatch)

        val hideDiscoverMore =
            newXSettings {
                category(Categories.CONTENT) {
                    group(Groups.CONTENT_FILTERING) {
                        toggle(
                            id = "newx.content.hide_discover_more",
                            strings = settingStrings("piko_newx_hide_discover_more"),
                            order = 250,
                            defaultValue = false,
                        )
                    }
                }
            }

        execute {
            val matches = NewXTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val read =
                    hideDiscoverMore.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    read.nextIndex,
                    """
                        invoke-static {p2, v${read.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterDiscoverMore(Ljava/lang/Object;Z)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }
