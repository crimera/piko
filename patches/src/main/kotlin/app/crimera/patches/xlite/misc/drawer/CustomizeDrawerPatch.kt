package app.crimera.patches.xlite.misc.drawer

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.MultiChoiceSettingDefinition
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteMultiChoice
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.DRAWER_ITEM_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.p0Register

private object XLiteDrawerContentClassFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("drawerState"),
            string("onBookmarkClicked"),
            string("onCommunitiesClicked"),
            string("onThemeSettingsClicked"),
        ),
)

private object XLiteDrawerMenuItemFingerprint : Fingerprint(
    classFingerprint = XLiteDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "Ljava/lang/String;",
            "L",
            "Lkotlin/jvm/functions/Function0;",
            "L",
            "Z",
            "L",
            "Landroidx/compose/runtime/Composer;",
            "I",
            "I",
        ),
)

private object XLiteDrawerFooterItemFingerprint : Fingerprint(
    classFingerprint = XLiteDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "Ljava/lang/String;",
            "L",
            "Lkotlin/jvm/functions/Function0;",
            "L",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
)

private fun MutableMethod.injectDrawerItemGuard(hiddenItems: MultiChoiceSettingDefinition) {
    val originalInstruction =
        instructions.firstOrNull()
            ?: throw PatchException("X-Lite drawer item renderer has no instructions")
    val titleParameterRegister = p0Register
    val titleRegister =
        getFreeRegisterProvider(0, 1, titleParameterRegister)
            .getFreeRegister4Bit()
    val read =
        hiddenItems.injectRead(
            method = this,
            index = 0,
            excludedRegisters = listOf(titleParameterRegister, titleRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val continueLabel = "piko_xlite_drawer_item_continue_${parameterTypes.size}"

    addInstructionsWithLabels(
        read.nextIndex,
        """
            move-object/from16 v$titleRegister, v$titleParameterRegister
            invoke-static {v$titleRegister, v${read.register}}, $DRAWER_ITEM_FILTER_DESCRIPTOR->shouldHide(Ljava/lang/String;Ljava/util/Set;)Z
            move-result v$titleRegister
            if-eqz v$titleRegister, :$continueLabel
            return-void
        """.trimIndent(),
        ExternalLabel(continueLabel, originalInstruction),
    )
}

@Suppress("unused")
val customizeXLiteDrawerPatch =
    bytecodePatch(
        name = "X-Lite: Customize drawer items",
        description = "Lets you hide selected items from the X-Lite navigation drawer.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hiddenItems =
            xLiteMultiChoice(
                id = "xlite.content.hidden_drawer_items",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_xlite_drawer"),
                order = 200,
                defaultValue = emptySet(),
                options =
                    listOf(
                        choice("PROFILE", "piko_xlite_drawer_profile"),
                        choice("PREMIUM", "piko_xlite_drawer_premium"),
                        choice("MONEY", "piko_xlite_drawer_money"),
                        choice("COMMUNITIES", "piko_xlite_drawer_communities"),
                        choice("BOOKMARKS", "piko_xlite_drawer_bookmarks"),
                        choice("COMMUNITY_NOTES", "piko_xlite_drawer_community_notes"),
                        choice("OFFLINE_VIDEOS", "piko_xlite_drawer_offline_videos"),
                        choice("LISTS", "piko_xlite_drawer_lists"),
                        choice("BOOST", "piko_xlite_drawer_boost"),
                        choice("SPACES", "piko_xlite_drawer_spaces"),
                        choice("FOLLOW_REQUESTS", "piko_xlite_drawer_follow_requests"),
                        choice("MONETIZATION", "piko_xlite_drawer_monetization"),
                        choice("CREATOR_STUDIO", "piko_xlite_drawer_creator_studio"),
                        choice("ANALYTICS", "piko_xlite_drawer_analytics"),
                        choice("SWITCH_TO_X", "piko_xlite_drawer_switch_to_x"),
                        choice("SETTINGS", "piko_xlite_drawer_settings"),
                        choice("HELP_CENTER", "piko_xlite_drawer_help_center"),
                        choice("FEEDBACK", "piko_xlite_drawer_feedback"),
                        choice("MEDIA_TRANSPARENCY", "piko_xlite_drawer_media_transparency"),
                        choice("IMPRINT", "piko_xlite_drawer_imprint"),
                    ),
            )

        execute {
            val classMatches = XLiteDrawerContentClassFingerprint.matchAll()
            if (classMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite drawer content class, found ${classMatches.size}: " +
                        classMatches.joinToString { it.originalMethod.toString() },
                )
            }

            listOf(XLiteDrawerMenuItemFingerprint, XLiteDrawerFooterItemFingerprint).forEach { fingerprint ->
                val matches = fingerprint.matchAll()
                if (matches.size != 1) {
                    throw PatchException(
                        "Expected one X-Lite drawer item renderer for ${fingerprint.javaClass.simpleName}, " +
                            "found ${matches.size}: " +
                            matches.joinToString { it.originalMethod.toString() },
                    )
                }
                matches.single().method.injectDrawerItemGuard(hiddenItems)
            }
        }
    }
