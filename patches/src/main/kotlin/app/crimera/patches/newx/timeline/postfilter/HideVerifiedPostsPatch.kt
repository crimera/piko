package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.multiChoice
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.timeline.NewXTimelineSuccessFingerprint
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideVerifiedPostsPatch =
    bytecodePatch(
        name = "NewX: Hide posts by verified account type",
        description = "Hides posts and replies authored by selected timeline-reported verification types.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineTextModelAdapterPatch)

        val verifiedTypesToHide =
            newXSettings {
                category(Categories.CONTENT) {
                    group(Groups.VERIFIED_ACCOUNT_FILTERING) {
                        val setting =
                            multiChoice(
                                id = "newx.content.hide_verified_account_types",
                                strings = settingStrings("piko_newx_hide_verified_account_types"),
                                order = 350,
                                defaultValue = emptySet(),
                                options =
                                    listOf(
                                        choice("Business", "piko_newx_hide_verified_account_types_business"),
                                        choice("Government", "piko_newx_hide_verified_account_types_government"),
                                        choice("User", "piko_newx_hide_verified_account_types_user"),
                                        choice("Unknown", "piko_newx_hide_verified_account_types_unknown"),
                                    ),
                            )
                        customScreen(
                            id = "newx.content.verified_account_whitelist",
                            strings = settingStrings("piko_newx_verified_account_whitelist"),
                            order = 360,
                            fragmentClassDescriptor =
                                "Lapp/morphe/extension/newx/postfilter/VerifiedAccountWhitelistFragment;",
                        )
                        setting
                    }
                }
            }

        execute {
            val matches = NewXTimelineSuccessFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val read =
                    verifiedTypesToHide.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    read.nextIndex,
                    """
                        invoke-static {p2, v${read.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterPostsByVerifiedType(Ljava/lang/Object;Ljava/util/Set;)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }
