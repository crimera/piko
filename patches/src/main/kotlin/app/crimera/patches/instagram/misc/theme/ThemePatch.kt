/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.util.findElementByAttributeValueOrThrow
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.FileWriter
import java.nio.file.Files

@Suppress("unused")
val themePatch =
    resourcePatch(
        name = "Theme",
        description = "Applies either amoled or material you theme for Instagram at patch time. [default = material you]",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val amoled by booleanOption(
            key = "amoled",
            default = false,
            title = "Pure-black AMOLED theme",
            description = "By default this patch applies a Material You dynamic theme that " +
                "follows your device's light/dark setting. Turn this on to override it " +
                "with a pure-black AMOLED theme instead. " +
                "Note: a few server-driven pages (notifications, DM inbox, " +
                "About-this-account) and full-screen media/Reels keep Instagram's own " +
                "colours in both modes.",
        )

        execute {
            forceWhiteOnMediaChrome()
            if (amoled == true) applyAmoledTheme() else applyMaterialYouTheme()
        }
    }

// On-media chrome — the feed post header (username / subtitle / follow / ⋯ menu),
// "Watch again" and similar labels drawn over photos/video — resolves through
// igds_color_primary_text_on_media / _icon_on_media / _primary_button_on_media,
// which all point at abc_decor_view_status_guard_light. That leaf is ALSO the dark
// app surface (igds_color_primary_background), so it can't be recoloured white
// (that turns the whole app light in dark mode). Instead repoint the on-media
// attributes themselves straight to white, leaving the shared surface leaf intact.
// On-media chrome sits over media (arbitrary/dark), so white is correct in every
// theme. Guarded so a missing/undecoded styles.xml can never fail the build.
private val onMediaChromeAttrs =
    setOf(
        "igds_color_primary_text_on_media",
        "igds_color_icon_on_media",
        "igds_color_primary_button_on_media",
    )

private fun ResourcePatchContext.forceWhiteOnMediaChrome() {
    listOf(
        "res/values/styles.xml",
        "res/values-night/styles.xml",
    ).forEach { path ->
        try {
            if (!get(path).exists()) return@forEach
            document(path).use { document ->
                val items = document.getElementsByTagName("item")
                for (index in 0 until items.length) {
                    val item = items.item(index) as? Element ?: continue
                    if (onMediaChromeAttrs.contains(item.getAttribute("name"))) {
                        item.textContent = "@color/bds_white"
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}

private fun ResourcePatchContext.applyAmoledTheme() {
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

private fun ResourcePatchContext.applyMaterialYouTheme() {
    // piko_dynamic_* alias palette. The alias NAMES are identical in every bucket
    // (so the token -> alias remaps further down apply uniformly), but the VALUES
    // differ: light tones in the day buckets, dark tones in the -night buckets, so
    // the theme follows the device's light/dark setting. The -v31 buckets use the
    // Android 12+ dynamic palette; the plain buckets carry fixed-hex fallbacks for
    // older devices.
    listOf(
        "res/values" to (materialYouLightFallbackAliases + materialYouNeutralConstantsHex),
        "res/values-night" to (materialYouDarkFallbackAliases + materialYouNeutralConstantsHex),
        "res/values-v31" to (materialYouLightDynamicAliases + materialYouNeutralConstantsDynamic),
        "res/values-night-v31" to (materialYouDarkDynamicAliases + materialYouNeutralConstantsDynamic),
    ).forEach { (directoryPath, aliases) ->
        ensureColorsXml(directoryPath)

        document("$directoryPath/colors.xml").use { document ->
            aliases.forEach { (name, value) ->
                document.upsertColor(name, value)
            }
        }
    }

    listOf(
        "res/values/colors.xml",
        "res/values-night/colors.xml",
        "res/values-v31/colors.xml",
        "res/values-night-v31/colors.xml",
    ).forEach { colorsFile ->
        document(colorsFile).use { document ->
            materialYouNamedMappings.forEach { (name, value) ->
                document.upsertColor(name, value)
            }
        }
    }
}

private fun ResourcePatchContext.ensureColorsXml(directoryPath: String) {
    val directory = get(directoryPath)
    if (!directory.isDirectory) {
        Files.createDirectories(directory.toPath())
    }

    val colorsXml = directory.resolve("colors.xml")
    if (!colorsXml.exists()) {
        FileWriter(colorsXml).use {
            it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
        }
    }
}

private fun Document.upsertColor(name: String, value: String) {
    val existingColor = findColor(name)
    if (existingColor != null) {
        existingColor.textContent = value
    } else {
        documentElement.appendChild(
            createElement("color").apply {
                setAttribute("name", name)
                textContent = value
            },
        )
    }
}

private fun Document.findColor(name: String): Element? {
    val colors = getElementsByTagName("color")
    for (index in 0 until colors.length) {
        val color = colors.item(index) as? Element ?: continue
        if (color.getAttribute("name") == name) {
            return color
        }
    }

    return null
}

// Fixed-hex fallbacks (pre-Android-12, no dynamic palette). LIGHT variant lives in
// res/values; the DARK variant in res/values-night. prism_black / prism_white are
// deliberately mode-invariant "constants" (a black-branded element stays dark and a
// white-branded element stays light in both modes) — every other role flips.
private val materialYouLightFallbackAliases =
    mapOf(
        "piko_dynamic_primary" to "#ff4557a5",
        "piko_dynamic_primary_pressed" to "#ff36468c",
        "piko_dynamic_primary_container" to "#ffdde1ff",
        "piko_dynamic_on_primary" to "#ffffffff",
        "piko_dynamic_on_primary_container" to "#ff001456",
        "piko_dynamic_background" to "#ffeef1f8",
        "piko_dynamic_pressed_background" to "#ffe2e6f0",
        "piko_dynamic_on_surface" to "#ff1a1c1f",
        "piko_dynamic_on_surface_variant" to "#ff44474e",
        "piko_dynamic_outline" to "#ff74777f",
        "piko_dynamic_outline_variant" to "#ffc4c6d0",
        "piko_dynamic_prism_black" to "#ff121316",
        "piko_dynamic_prism_white" to "#ffeef1f8",
    )

private val materialYouDarkFallbackAliases =
    mapOf(
        "piko_dynamic_primary" to "#ff8ea0ff",
        "piko_dynamic_primary_pressed" to "#ffc0c7ff",
        "piko_dynamic_primary_container" to "#ff31448f",
        "piko_dynamic_on_primary" to "#ff101a3f",
        "piko_dynamic_on_primary_container" to "#ffe0e0ff",
        "piko_dynamic_background" to "#ff121316",
        "piko_dynamic_pressed_background" to "#ff1d1f22",
        "piko_dynamic_on_surface" to "#ffe1e3e6",
        "piko_dynamic_on_surface_variant" to "#ffc1c7cf",
        "piko_dynamic_outline" to "#ff8b929b",
        "piko_dynamic_outline_variant" to "#ff3f4750",
        "piko_dynamic_prism_black" to "#ff121316",
        "piko_dynamic_prism_white" to "#ffe1e3e6",
    )

// Android 12+ dynamic palette. Lower tone number = lighter (neutral1_0 ≈ white,
// neutral1_1000 ≈ black), so the light and dark variants pull opposite ends of the
// same wallpaper-derived ramp. Surfaces use the NEUTRAL palette (system_neutral1_*)
// rather than accent, so the background doesn't pick up the wallpaper's colour cast
// — a calmer backdrop for a photo-first app.
private val materialYouLightDynamicAliases =
    mapOf(
        "piko_dynamic_primary" to "@android:color/system_accent1_600",
        "piko_dynamic_primary_pressed" to "@android:color/system_accent1_700",
        "piko_dynamic_primary_container" to "@android:color/system_accent1_100",
        "piko_dynamic_on_primary" to "@android:color/system_neutral1_10",
        "piko_dynamic_on_primary_container" to "@android:color/system_accent1_900",
        // Light surfaces use the PRIMARY accent palette (accent1) at a light tone so
        // the wallpaper colour is actually visible, instead of the near-white
        // system_neutral1_10 (reads as plain white) or the muted accent2 (barely
        // tinted). Both light-surface paths get it — background AND prism_white (the
        // latter is what the main feed bg, default_bg_color_light -> bds_white,
        // resolves through) — so the tint is uniform. Dial by tone/palette:
        // system_accent2_50 = very subtle, system_accent1_50 = current (light tint),
        // system_accent1_100 = clearly coloured (deeper), system_neutral1_50 = grey/
        // no colour. on_surface text stays neutral for readable contrast.
        "piko_dynamic_background" to "@android:color/system_accent1_50",
        "piko_dynamic_pressed_background" to "@android:color/system_accent1_100",
        "piko_dynamic_on_surface" to "@android:color/system_neutral1_900",
        "piko_dynamic_on_surface_variant" to "@android:color/system_neutral2_700",
        "piko_dynamic_outline" to "@android:color/system_neutral2_500",
        "piko_dynamic_outline_variant" to "@android:color/system_neutral2_200",
        "piko_dynamic_prism_black" to "@android:color/system_neutral1_900",
        "piko_dynamic_prism_white" to "@android:color/system_accent1_50",
    )

private val materialYouDarkDynamicAliases =
    mapOf(
        "piko_dynamic_primary" to "@android:color/system_accent1_200",
        "piko_dynamic_primary_pressed" to "@android:color/system_accent1_100",
        // Colored container: one tone darker (700 -> 800) so tinted surfaces sit
        // lower/dimmer against the neutral background below.
        "piko_dynamic_primary_container" to "@android:color/system_accent1_800",
        "piko_dynamic_on_primary" to "@android:color/system_neutral1_900",
        "piko_dynamic_on_primary_container" to "@android:color/system_accent1_100",
        // The dynamic palette only exposes discrete tones: _900 (≈ dark grey) and
        // _1000 (near-black). To go darker still, flip the two "…neutral1_900" lines
        // below to "…neutral1_1000" (near-black — close to the AMOLED theme, but
        // keeps dynamic accents).
        "piko_dynamic_background" to "@android:color/system_neutral1_900",
        "piko_dynamic_pressed_background" to "@android:color/system_neutral1_800",
        "piko_dynamic_on_surface" to "@android:color/system_neutral1_10",
        "piko_dynamic_on_surface_variant" to "@android:color/system_neutral2_200",
        "piko_dynamic_outline" to "@android:color/system_neutral2_400",
        "piko_dynamic_outline_variant" to "@android:color/system_neutral2_700",
        "piko_dynamic_prism_black" to "@android:color/system_neutral1_900",
        "piko_dynamic_prism_white" to "@android:color/system_neutral1_10",
    )

// Greyscale CONSTANTS — identical in the light and dark buckets (so they do NOT
// flip with the device theme). Literal grey/black/white *scale* tokens must map to
// these, never to the mode-flipping aliases above, because Instagram reuses the
// same literal for OPPOSITE roles per mode: e.g. bds_black is a dark surface in
// dark mode but dark TEXT/ICONS in light mode (igds_color_primary_icon ->
// ?igds_color_primary_text -> bds_black in the light theme). A constant keeps such
// a token the right lightness in both modes; IG's own theme decides which literal
// to use where. The extremes reuse piko_dynamic_prism_white / prism_black; only
// three mid-greys are added here. neutral2 tones = the wallpaper-derived neutral
// ramp (pre-12 hex fallbacks mirror the old dark tones so dark mode is unchanged).
private val materialYouNeutralConstantsHex =
    mapOf(
        "piko_dynamic_neutral_light" to "#ffc1c7cf",
        "piko_dynamic_neutral_mid" to "#ff8b929b",
        "piko_dynamic_neutral_dark" to "#ff3f4750",
    )

private val materialYouNeutralConstantsDynamic =
    mapOf(
        "piko_dynamic_neutral_light" to "@android:color/system_neutral2_200",
        "piko_dynamic_neutral_mid" to "@android:color/system_neutral2_400",
        "piko_dynamic_neutral_dark" to "@android:color/system_neutral2_700",
    )

// Literal greyscale tokens (bds_grey_0 lightest .. bds_grey_24 darkest;
// igds_prism_gray_00 lightest .. _1500 darkest — verified against the 435.x table)
// map to the fixed CONSTANTS above by lightness, NOT to the mode-flipping aliases.
// The accent tokens (bds_blue_*, badge/emphasized, prism_indigo) DO flip, because
// IG uses the same accent literal on both light and dark surfaces and it needs a
// different tone for contrast in each mode.
private val materialYouBaseMappings =
    mapOf(
        "bds_black" to "@color/piko_dynamic_prism_black",
        "bds_white" to "@color/piko_dynamic_prism_white",
        "bds_grey_0" to "@color/piko_dynamic_prism_white",
        "bds_grey_1" to "@color/piko_dynamic_prism_white",
        "bds_grey_2" to "@color/piko_dynamic_neutral_light",
        "bds_grey_3" to "@color/piko_dynamic_neutral_light",
        "bds_grey_4" to "@color/piko_dynamic_neutral_mid",
        "bds_grey_6" to "@color/piko_dynamic_neutral_dark",
        "bds_grey_7" to "@color/piko_dynamic_prism_black",
        "bds_grey_8" to "@color/piko_dynamic_prism_black",
        "bds_grey_9" to "@color/piko_dynamic_prism_black",
        "bds_grey_10" to "@color/piko_dynamic_prism_black",
        "bds_grey_11" to "@color/piko_dynamic_prism_black",
        "bds_grey_12" to "@color/piko_dynamic_prism_black",
        "bds_grey_16" to "@color/piko_dynamic_prism_black",
        "bds_grey_18" to "@color/piko_dynamic_prism_black",
        "bds_grey_21" to "@color/piko_dynamic_prism_black",
        "bds_grey_22" to "@color/piko_dynamic_prism_black",
        "bds_grey_24" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_00" to "@color/piko_dynamic_prism_white",
        "igds_prism_gray_08" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_10" to "@color/piko_dynamic_prism_black",
        "bds_blue_0" to "@color/piko_dynamic_primary",
        "bds_blue_1" to "@color/piko_dynamic_primary_pressed",
        "bds_blue_2" to "@color/piko_dynamic_primary_container",
        // Semantic snackbar surface — flips with the theme like other surfaces.
        "bottom_sheet_undo_redo_color" to "@color/piko_dynamic_background",
        "emphasized_action_color" to "@color/piko_dynamic_primary",
        "badge_color" to "@color/piko_dynamic_primary",
        "igds_prism_indigo_1000" to "@color/piko_dynamic_primary_container",
    )

private val materialYouAccentMappings =
    mapOf(
        "igds_primary_button" to "@color/piko_dynamic_primary",
        "igds_link" to "@color/piko_dynamic_primary",
        "igds_prism_black" to "@color/piko_dynamic_prism_black",
        "fds_blue_60" to "@color/piko_dynamic_primary",
    )

// Real IGDS colour-resource tokens (verified present in the target APK). NOTE:
// the flat igds_*_background surfaces (bottom sheet / dialog / menu / action
// sheet / modal / popover / toast / list / cell / row / etc.) and their
// igds_color_* twins were REMOVED here — Instagram has no such colour resources
// (upsertColor was silently creating dead entries). Those surfaces are themed
// via the ?attr/igds_color_*_background theme-attribute chains, whose grey
// leaves are remapped in materialYouSurfaceBaselineMappings below.
private val materialYouThemeMappings =
    mapOf(
        "igds_primary_background" to "@color/piko_dynamic_background",
        "igds_secondary_background" to "@color/piko_dynamic_background",
        "igds_elevated_background" to "@color/piko_dynamic_background",
        "igds_elevated_highlight_background" to "@color/piko_dynamic_background",
        "igds_elevated_separator" to "@color/piko_dynamic_outline_variant",
        "igds_separator" to "@color/piko_dynamic_outline_variant",
        "igds_stroke" to "@color/piko_dynamic_outline",
        "igds_primary_text" to "@color/piko_dynamic_on_surface",
        "igds_secondary_text" to "@color/piko_dynamic_on_surface_variant",
        "igds_primary_icon" to "@color/piko_dynamic_on_surface",
        "igds_secondary_icon" to "@color/piko_dynamic_on_surface_variant",
    )

// Surfaces like popup/context menus, alert dialogs, action/bottom sheets and
// toasts do NOT resolve through the igds_* surface tokens above. They flow
// through Instagram's GM3/"baseline" Material substrate — a separate colour
// chain that the igds mappings never reach — which is why those pages (and the
// activity/notifications feed, which uses the generic default_bg_color) kept
// rendering in dark grey after the rest of the app was themed.
//
// The chains bottom out here (values verified against the patched 435.x APK):
//   dialog_bg_color_dark_baseline  -> default_bg_color_dark_elev_3 -> baseline_neutral_10_..._alpha_11 (#ff313336)
//   menu_item_bg_color_dark_baseline -> default_bg_color_dark_elev_5 -> baseline_neutral_10_..._alpha_14 (#ff37393d)
//   toast_bg_color (night)         -> default_bg_color_dark_elev_1 -> baseline_neutral_10_..._alpha_5  (#ff28282a)
//   default_bg_color_dark          -> gm3_baseline_surface_dark    -> baseline_neutral_10_..._alpha_5  (#ff28282a)
//   menu_bg_color_baseline         -> gm3_baseline_surface_container_dark (#ff1e1f20)
// Remapping the leaves fixes every surface downstream in one place. The alpha_*
// tokens are misleadingly named — their shipped values are fully opaque, so
// pointing them at the opaque dynamic background loses no translucency.
private val materialYouSurfaceBaselineMappings =
    mapOf(
        "gm3_baseline_surface_dark" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_container_dark" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_5" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_11" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_14" to "@color/piko_dynamic_background",
        // Bottom-sheet variant that hard-codes a raw grey (#ff1c1f24) instead of
        // going through igds_* / default_bg_color tokens.
        "basel_bottom_sheet_background_color" to "@color/piko_dynamic_background",
        // IGDS context / creation menus resolve to translucent prism-gray / bds-grey
        // leaves not covered by the plain igds_prism_gray_* / bds_grey_* remaps.
        // Remap the surface tokens directly so their pressed-state item overlays
        // (subtle 5% alpha tokens) are left intact.
        "igds_context_menu_background_color" to "@color/piko_dynamic_background",
        "igds_creation_menu_background" to "@color/piko_dynamic_background",
        // AppCompat / Material Components (MDC) surface greys. Alert dialogs,
        // popup & context menus, and MDC bottom sheets don't read the igds_*/gm3_*
        // colour RESOURCES above — they resolve MDC theme ATTRIBUTES set in
        // Theme.Instagram: colorSurface -> design_dark_default_color_background
        // (#ff121212), and colorBackgroundFloating -> background_floating_material_dark
        // -> cardview_dark_background (#ff424242). background_material_dark falls
        // through to material_grey_850/900. Those attribute chains bottom out at
        // these four leaves, so they are the dark greys that survived every other
        // remap. (elevationOverlayColor is ?attr/colorOnSurface with the overlay
        // enabled, so elevated MDC surfaces may still show a faint tonal lift on
        // top of the dynamic background — that's the intended Material elevation
        // look, not the flat grey being fixed here.)
        "design_dark_default_color_background" to "@color/piko_dynamic_background",
        "cardview_dark_background" to "@color/piko_dynamic_background",
        "material_grey_850" to "@color/piko_dynamic_background",
        "material_grey_900" to "@color/piko_dynamic_background",
        // IGDS surfaces — bottom sheets, dialogs, banners, toasts, and the
        // secondary/notification cells — do NOT read the igds_color_* colour
        // RESOURCES (those were phantom entries the patch created). Their shape
        // drawables (igds_bottom_sheet_background, igds_dialog_bg, …) fill via the
        // theme ATTRIBUTE ?attr/igds_color_elevated_background (and the
        // _secondary_/_highlight_background attrs). In dark mode the active
        // overlays — IgdsElevatedBackgroundFixDark / IgdsPrismGrayOverridesDark /
        // StoryCommentColorsDark — point those attrs at the dark prism-gray leaves
        // below. prism_gray_08/10 and bds_grey_7..24 were already remapped, but
        // these darker elevated steps were missed, which is why the sheets/dialogs
        // stayed grey no matter what the colour resources said.
        // Dark prism-greys: these are dark-mode elevated surfaces, but IG also uses
        // the mid-dark ones as dark FOREGROUND (icons/secondary text) in light mode,
        // so they must be a dark CONSTANT, not the mode-flipping background (which
        // would flip them to near-white and hide them in light mode).
        "igds_prism_gray_07" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_09" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_13" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_14" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_1500" to "@color/piko_dynamic_prism_black",
        // Near-opaque (>=80% alpha) surface/panel variants used for banners,
        // toasts, notifications and the creation menu. Flattening to the opaque
        // dynamic background is imperceptible at these alphas. The low-alpha
        // hover / pressed / scrim tints (…_alpha_50, …_70_transparent,
        // white_*_transparent, bds_black_*_transparent) are deliberately left
        // translucent so ripples and overlays keep working.
        "igds_prism_gray_09_alpha_95" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_10_alpha_95" to "@color/piko_dynamic_prism_black",
        "bds_grey_9_95_transparent" to "@color/piko_dynamic_prism_black",
        "bds_grey_10_80_transparent" to "@color/piko_dynamic_prism_black",
        "bds_grey_10_90_transparent" to "@color/piko_dynamic_prism_black",
        // LIGHT-mode counterparts of the dark surface leaves above (all confirmed
        // present in the 435.x resource table). In light mode Instagram resolves the
        // same attribute chains to the "_light" / non-"_dark" twins, so the day
        // buckets need them remapped too or light-mode surfaces fall through to stock
        // white. These stay on the mode-flipping background alias on purpose: in the
        // light buckets that resolves to the light dynamic surface (correct), and in
        // the -night buckets it's the dark surface — harmless, since IG doesn't read
        // the light leaves in dark mode. (The main light window bg, default_bg_color_
        // light -> bds_white, is already covered by the bds_white constant above.)
        "gm3_baseline_surface" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_light" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_container" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_container_light" to "@color/piko_dynamic_background",
        "baseline_neutral_100_with_surface_tint_light_alpha_5" to "@color/piko_dynamic_background",
        "baseline_neutral_100_with_surface_tint_light_alpha_11" to "@color/piko_dynamic_background",
        "baseline_neutral_100_with_surface_tint_light_alpha_12" to "@color/piko_dynamic_background",
        "baseline_neutral_100_with_surface_tint_light_alpha_14" to "@color/piko_dynamic_background",
        "design_default_color_background" to "@color/piko_dynamic_background",
        "cardview_light_background" to "@color/piko_dynamic_background",
        "material_grey_50" to "@color/piko_dynamic_background",
        "material_grey_100" to "@color/piko_dynamic_background",
        // The big light-mode gap: in the light theme, MDC colorSurface AND one
        // igds_color_primary_background variant resolve to abc_decor_view_status_
        // guard_light (raw #ffffff), and colorBackgroundFloating ->
        // background_floating_material_light (also raw white). MDC bottom sheets,
        // alert dialogs and the feed pull colorSurface / colorBackgroundFloating, so
        // these stayed pure white while every documented igds_/bds_ chain was themed.
        // (Dark analogues design_dark_default_color_background / cardview_dark_* /
        // material_grey_850/900 were already mapped above.)
        //
        // abc_decor_view_status_guard_light is OVERLOADED and CONFLICTED: it's the
        // light colorSurface / an igds_color_primary_background variant — a mode-
        // flipping app SURFACE (must be dark in -night) — AND the on-media text/icon
        // colour (igds_color_primary_text_on_media/_icon_on_media, wants light always).
        // The SURFACE role wins: mapping it to a light constant turned the whole app
        // light in dark mode. So it stays on the mode-flipping background. The cost is
        // on-media text ("Watch again" / on-video labels) rendering dark in dark mode
        // — a minor media-overlay issue in the same unreachable class as the
        // notifications/Bloks surfaces (a single leaf can't be both a dark surface and
        // light text).
        "abc_decor_view_status_guard_light" to "@color/piko_dynamic_background",
        // Pure surface (colorBackgroundFloating) — not reused for text — so the
        // mode-flipping background is fine here.
        "background_floating_material_light" to "@color/piko_dynamic_background",
        // Light near-white prism-greys used as light-mode surfaces / secondary
        // panels (stock #f3f5f7 / #e9edf0). Map to the light constant so they follow
        // the light surface tint (and remain light text in dark mode). prism_gray_03
        // (#dbdfe4) is a light divider tone -> the light-grey constant.
        "igds_prism_gray_01" to "@color/piko_dynamic_prism_white",
        "igds_prism_gray_02" to "@color/piko_dynamic_prism_white",
        "igds_prism_gray_03" to "@color/piko_dynamic_neutral_light",
    )

private val materialYouNamedMappings =
    materialYouBaseMappings +
        materialYouAccentMappings +
        materialYouThemeMappings +
        materialYouSurfaceBaselineMappings
