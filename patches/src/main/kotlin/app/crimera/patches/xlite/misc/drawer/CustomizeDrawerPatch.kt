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
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.p0Register

private object XLiteDrawerContentClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/drawer/",
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

// ALPHA PATH: legacy drawer rows pass their localized title to the renderer.
// TODO: Remove this fingerprint and injectDrawerItemGuard when alpha is deprecated.
private object XLiteDrawerFooterItemFingerprint : Fingerprint(
    classFingerprint = XLiteDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "I",
            "Landroidx/compose/runtime/Composer;",
            "L",
            "L",
            "Ljava/lang/String;",
            "Lkotlin/jvm/functions/Function0;",
        ),
)

/**
 * BETA PATH: renders the settings footer without passing its title as a parameter.
 * Keep this path as the source for future drawer updates.
 */
private object XLiteDrawerSettingsFooterItemFingerprint : Fingerprint(
    classFingerprint = XLiteDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "I",
            "Landroidx/compose/runtime/Composer;",
            "Landroidx/compose/ui/Modifier;",
            "Lkotlin/jvm/functions/Function0;",
        ),
    filters =
        listOf(
            methodCall(
                definingClass = "Landroidx/compose/material3/",
                parameters =
                    listOf(
                        "Lkotlin/jvm/functions/Function0;",
                        "Landroidx/compose/ui/Modifier;",
                        "Z",
                        "L",
                        "L",
                        "Lkotlin/jvm/functions/Function2;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                        "I",
                    ),
                returnType = "V",
            ),
        ),
)

// ALPHA/LEGACY PATH: title-based filtering for rows that still expose localized titles.
// TODO: Remove this path only when no supported release uses title-based drawer rows.
private fun MutableMethod.injectDrawerItemGuard(hiddenItems: MultiChoiceSettingDefinition) {
    val stringParamIndex =
        parameterTypes.indexOf("Ljava/lang/String;").takeIf { it >= 0 }
            ?: throw PatchException("X-Lite drawer item renderer does not have a String parameter: $this")
    val precedingRegisters =
        parameterTypes.subList(0, stringParamIndex).sumOf { type ->
            if (type == "J" || type == "D") 2 else 1
        }
    val titleParameterRegister = p0Register + precedingRegisters
    val titleRegister =
        getFreeRegisterProvider(0, 1, titleParameterRegister)
            .getFreeRegister4Bit()
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        titleRegister = titleRegister,
        titleInstruction = "move-object/from16 v$titleRegister, v$titleParameterRegister",
        predicateMethod = "shouldHide",
        labelSuffix = "${parameterTypes.size}_$stringParamIndex",
        excludedRegisters = listOf(titleParameterRegister, titleRegister),
    )
}

// BETA PATH: ID-based filtering for the title-less settings footer.
private fun MutableMethod.injectFixedDrawerItemGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    itemId: String,
) {
    val parameterRegisterCount =
        parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    val titleRegister =
        getFreeRegisterProvider(0, 1, *(0 until parameterRegisterCount).toList().toIntArray())
            .getFreeRegister4Bit()
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        titleRegister = titleRegister,
        titleInstruction = "const-string v$titleRegister, \"$itemId\"",
        predicateMethod = "shouldHideId",
        labelSuffix = "fixed_$itemId",
        excludedRegisters = listOf(titleRegister),
    )
}

private fun MutableMethod.injectDrawerGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    titleRegister: Int,
    titleInstruction: String,
    predicateMethod: String,
    labelSuffix: String,
    excludedRegisters: List<Int>,
) {
    val originalInstruction =
        instructions.firstOrNull()
            ?: throw PatchException("X-Lite drawer item renderer has no instructions")
    val read =
        hiddenItems.injectRead(
            method = this,
            index = 0,
            excludedRegisters = excludedRegisters,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val continueLabel = "piko_xlite_drawer_item_continue_$labelSuffix"

    addInstructionsWithLabels(
        read.nextIndex,
        """
            $titleInstruction
            invoke-static {v$titleRegister, v${read.register}}, $DRAWER_ITEM_FILTER_DESCRIPTOR->$predicateMethod(Ljava/lang/String;Ljava/util/Set;)Z
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
            val classMatches = XLiteDrawerContentClassFingerprint.scopedMatchAll()
            if (classMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite drawer content class, found ${classMatches.size}: " +
                        classMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val menuMatches = XLiteDrawerMenuItemFingerprint.scopedMatchAll()
            if (menuMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite drawer menu item renderer, found ${menuMatches.size}: " +
                        menuMatches.joinToString { it.originalMethod.toString() },
                )
            }
            menuMatches.single().method.injectDrawerItemGuard(hiddenItems)

            // ALPHA PATH: prefer the legacy title-based footer whenever it exists.
            val legacyFooterMatches =
                XLiteDrawerFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
            // BETA PATH: use the title-less settings footer only when the alpha shape is absent.
            val settingsFooterMatches =
                XLiteDrawerSettingsFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
            val footerMatches =
                if (legacyFooterMatches.isNotEmpty()) {
                    // ALPHA PATH: select the legacy title-based footer.
                    legacyFooterMatches
                } else {
                    // BETA PATH: select the title-less settings footer.
                    settingsFooterMatches
                }
            if (footerMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite drawer footer renderer across known shapes, found " +
                        "${footerMatches.size}: ${footerMatches.joinToString { it.originalMethod.toString() }}",
                )
            }
            val footerMatch = footerMatches.single()
            // BETA PATH: pass the stable SETTINGS ID instead of reading a title.
            if (footerMatch.originalMethod.toString() in
                settingsFooterMatches.map { it.originalMethod.toString() }
            ) {
                footerMatch.method.injectFixedDrawerItemGuard(hiddenItems, "SETTINGS")
            } else {
                // ALPHA PATH: read the localized title from the legacy footer signature.
                footerMatch.method.injectDrawerItemGuard(hiddenItems)
            }
        }
    }
