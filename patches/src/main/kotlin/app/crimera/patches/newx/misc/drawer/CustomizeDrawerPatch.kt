package app.crimera.patches.newx.misc.drawer

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.MultiChoiceSettingDefinition
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.DRAWER_ITEM_FILTER_DESCRIPTOR
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

private object NewXDrawerContentClassFingerprint : Fingerprint(
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

private object NewXDrawerMenuItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
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
private object NewXDrawerFooterItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
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
private object NewXDrawerSettingsFooterItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
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

/**
 * GROK PATH: dedicated "Get Grok / Open Grok" button rendered without a title parameter.
 * Title is resolved inside from drawer_get_grok / drawer_open_grok, so filter by stable ID.
 */
private object NewXDrawerGrokButtonFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "L",
            "Lkotlin/jvm/functions/Function1;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
)

// ALPHA/LEGACY PATH: title-based filtering for rows that still expose localized titles.
// TODO: Remove this path only when no supported release uses title-based drawer rows.
private fun MutableMethod.injectDrawerItemGuard(hiddenItems: MultiChoiceSettingDefinition) {
    val stringParamIndex =
        parameterTypes.indexOf("Ljava/lang/String;").takeIf { it >= 0 }
            ?: throw PatchException("NewX drawer item renderer does not have a String parameter: $this")
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
            ?: throw PatchException("NewX drawer item renderer has no instructions")
    val read =
        hiddenItems.injectRead(
            method = this,
            index = 0,
            excludedRegisters = excludedRegisters,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val continueLabel = "piko_newx_drawer_item_continue_$labelSuffix"

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
val customizeNewXDrawerPatch =
    bytecodePatch(
        name = "NewX: Customize drawer items",
        description = "Lets you hide selected items from the NewX navigation drawer.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hiddenItems =
            newXMultiChoice(
                id = "newx.content.hidden_drawer_items",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_drawer"),
                order = 200,
                defaultValue = emptySet(),
                options =
                    listOf(
                        choice("PROFILE", "piko_newx_drawer_profile"),
                        choice("PREMIUM", "piko_newx_drawer_premium"),
                        choice("MONEY", "piko_newx_drawer_money"),
                        choice("COMMUNITIES", "piko_newx_drawer_communities"),
                        choice("BOOKMARKS", "piko_newx_drawer_bookmarks"),
                        choice("COMMUNITY_NOTES", "piko_newx_drawer_community_notes"),
                        choice("OFFLINE_VIDEOS", "piko_newx_drawer_offline_videos"),
                        choice("LISTS", "piko_newx_drawer_lists"),
                        choice("BOOST", "piko_newx_drawer_boost"),
                        choice("SPACES", "piko_newx_drawer_spaces"),
                        choice("FOLLOW_REQUESTS", "piko_newx_drawer_follow_requests"),
                        choice("MONETIZATION", "piko_newx_drawer_monetization"),
                        choice("CREATOR_STUDIO", "piko_newx_drawer_creator_studio"),
                        choice("ANALYTICS", "piko_newx_drawer_analytics"),
                        choice("SWITCH_TO_X", "piko_newx_drawer_switch_to_x"),
                        choice("GROK", "piko_newx_drawer_grok"),
                        choice("SETTINGS", "piko_newx_drawer_settings"),
                        choice("HELP_CENTER", "piko_newx_drawer_help_center"),
                        choice("FEEDBACK", "piko_newx_drawer_feedback"),
                        choice("MEDIA_TRANSPARENCY", "piko_newx_drawer_media_transparency"),
                        choice("IMPRINT", "piko_newx_drawer_imprint"),
                    ),
            )

        execute {
            val classMatches = NewXDrawerContentClassFingerprint.scopedMatchAll()
            if (classMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX drawer content class, found ${classMatches.size}: " +
                        classMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val menuMatches = NewXDrawerMenuItemFingerprint.scopedMatchAll()
            if (menuMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX drawer menu item renderer, found ${menuMatches.size}: " +
                        menuMatches.joinToString { it.originalMethod.toString() },
                )
            }
            menuMatches.single().method.injectDrawerItemGuard(hiddenItems)

            // ALPHA PATH: prefer the legacy title-based footer whenever it exists.
            val legacyFooterMatches =
                NewXDrawerFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
            // BETA PATH: use the title-less settings footer only when the alpha shape is absent.
            val settingsFooterMatches =
                NewXDrawerSettingsFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
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
                    "Expected one NewX drawer footer renderer across known shapes, found " +
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

            // GROK PATH: dedicated Get/Open Grok button; skip when the release has no Grok row.
            val grokMatches = NewXDrawerGrokButtonFingerprint.scopedMatchAllOrNull().orEmpty()
            if (grokMatches.size > 1) {
                throw PatchException(
                    "Expected at most one NewX drawer Grok button, found ${grokMatches.size}: " +
                        grokMatches.joinToString { it.originalMethod.toString() },
                )
            }
            grokMatches.singleOrNull()?.method?.injectFixedDrawerItemGuard(hiddenItems, "GROK")
        }
    }
