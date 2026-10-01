/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

internal const val MATERIAL_YOU_LIGHT_SWITCH_OFF_TRACK_FALLBACK = "#ffe2e6f0"
internal const val MATERIAL_YOU_DARK_SWITCH_OFF_TRACK_FALLBACK = "#ff2b3036"
internal const val MATERIAL_YOU_LIGHT_SWITCH_OFF_TRACK_DYNAMIC =
    "@android:color/system_neutral1_100"
internal const val MATERIAL_YOU_DARK_SWITCH_OFF_TRACK_DYNAMIC =
    "@android:color/system_neutral1_800"

internal val amoledSwitchMappings =
    mapOf(
        "material_unselected_track" to "#ff5e646d",
        "material_track_border" to "#ffa2aab4",
        "checkbox_unchecked_enabled" to "#ffa2aab4",
    )

// Static fallbacks used when Android dynamic colors are unavailable.
internal val materialYouLightFallbackAliases =
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
        "piko_dynamic_switch_off_track" to MATERIAL_YOU_LIGHT_SWITCH_OFF_TRACK_FALLBACK,
        "piko_dynamic_prism_black" to "#ff121316",
        "piko_dynamic_prism_white" to "#ffeef1f8",
    )

internal val materialYouDarkFallbackAliases =
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
        "piko_dynamic_switch_off_track" to MATERIAL_YOU_DARK_SWITCH_OFF_TRACK_FALLBACK,
        "piko_dynamic_prism_black" to "#ff121316",
        "piko_dynamic_prism_white" to "#ffe1e3e6",
    )

private val materialYouLightDynamicAliases =
    mapOf(
        "piko_dynamic_primary" to "@android:color/system_accent1_600",
        "piko_dynamic_primary_pressed" to "@android:color/system_accent1_700",
        "piko_dynamic_primary_container" to "@android:color/system_accent1_100",
        "piko_dynamic_on_primary" to "@android:color/system_neutral1_10",
        "piko_dynamic_on_primary_container" to "@android:color/system_accent1_900",
        "piko_dynamic_background" to "@android:color/system_accent1_50",
        "piko_dynamic_pressed_background" to "@android:color/system_accent1_100",
        "piko_dynamic_on_surface" to "@android:color/system_neutral1_900",
        "piko_dynamic_on_surface_variant" to "@android:color/system_neutral2_700",
        "piko_dynamic_outline" to "@android:color/system_neutral2_500",
        "piko_dynamic_outline_variant" to "@android:color/system_neutral2_200",
        "piko_dynamic_switch_off_track" to MATERIAL_YOU_LIGHT_SWITCH_OFF_TRACK_DYNAMIC,
        "piko_dynamic_prism_black" to "@android:color/system_neutral1_900",
        "piko_dynamic_prism_white" to "@android:color/system_accent1_50",
    )

private val materialYouDarkDynamicAliases =
    mapOf(
        "piko_dynamic_primary" to "@android:color/system_accent1_200",
        "piko_dynamic_primary_pressed" to "@android:color/system_accent1_100",
        "piko_dynamic_primary_container" to "@android:color/system_accent1_800",
        "piko_dynamic_on_primary" to "@android:color/system_neutral1_900",
        "piko_dynamic_on_primary_container" to "@android:color/system_accent1_100",
        "piko_dynamic_background" to "@android:color/system_neutral1_900",
        "piko_dynamic_pressed_background" to "@android:color/system_neutral1_800",
        "piko_dynamic_on_surface" to "@android:color/system_neutral1_10",
        "piko_dynamic_on_surface_variant" to "@android:color/system_neutral2_200",
        "piko_dynamic_outline" to "@android:color/system_neutral2_400",
        "piko_dynamic_outline_variant" to "@android:color/system_neutral2_700",
        "piko_dynamic_switch_off_track" to MATERIAL_YOU_DARK_SWITCH_OFF_TRACK_DYNAMIC,
        "piko_dynamic_prism_black" to "@android:color/system_neutral1_900",
        "piko_dynamic_prism_white" to "@android:color/system_neutral1_10",
    )

// Instagram reuses literal greys for opposite roles across themes, so these stay mode-invariant.
internal val materialYouNeutralConstantsHex =
    mapOf(
        "piko_dynamic_neutral_light" to "#ffc1c7cf",
        "piko_dynamic_neutral_mid" to "#ff8b929b",
        "piko_dynamic_neutral_dark" to "#ff3f4750",
        "piko_dynamic_neutral_deep" to "#ff2b3036",
    )

private val materialYouNeutralConstantsDynamic =
    mapOf(
        "piko_dynamic_neutral_light" to "@android:color/system_neutral2_200",
        "piko_dynamic_neutral_mid" to "@android:color/system_neutral2_400",
        "piko_dynamic_neutral_dark" to "@android:color/system_neutral2_700",
        "piko_dynamic_neutral_deep" to "@android:color/system_neutral2_800",
    )

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
        "igds_prism_gray_08" to "@color/piko_dynamic_neutral_deep",
        "igds_prism_gray_10" to "@color/piko_dynamic_prism_black",
        "bds_blue_0" to "@color/piko_dynamic_primary",
        "bds_blue_1" to "@color/piko_dynamic_primary_pressed",
        "bds_blue_2" to "@color/piko_dynamic_primary_container",
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

private val materialYouThemeMappings =
    mapOf(
        "igds_primary_background" to "@color/piko_dynamic_background",
        "igds_secondary_background" to "@color/piko_dynamic_background",
        "igds_elevated_background" to "@color/piko_dynamic_background",
        "igds_elevated_highlight_background" to "@color/piko_dynamic_pressed_background",
        "igds_elevated_separator" to "@color/piko_dynamic_outline_variant",
        "igds_separator" to "@color/piko_dynamic_outline_variant",
        "igds_stroke" to "@color/piko_dynamic_outline",
        "igds_primary_text" to "@color/piko_dynamic_on_surface",
        "igds_secondary_text" to "@color/piko_dynamic_on_surface_variant",
        "igds_primary_icon" to "@color/piko_dynamic_on_surface",
        "igds_secondary_icon" to "@color/piko_dynamic_on_surface_variant",
        "material_selected_track" to "@color/piko_dynamic_primary",
        "checkbox_image_tint" to "@color/piko_dynamic_on_primary",
        "material_unselected_track" to "@color/piko_dynamic_switch_off_track",
        "material_track_border" to "@color/piko_dynamic_outline",
        "checkbox_unchecked_enabled" to "@color/piko_dynamic_outline",
    )

// Some surfaces resolve through GM3/AppCompat leaves instead of the IGDS aliases above.
private val materialYouSurfaceBaselineMappings =
    mapOf(
        "gm3_baseline_surface_dark" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_container_dark" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_5" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_11" to "@color/piko_dynamic_background",
        "baseline_neutral_10_with_surface_tint_dark_alpha_14" to "@color/piko_dynamic_background",
        "basel_bottom_sheet_background_color" to "@color/piko_dynamic_background",
        "igds_context_menu_background_color" to "@color/piko_dynamic_background",
        "igds_creation_menu_background" to "@color/piko_dynamic_background",
        "design_dark_default_color_background" to "@color/piko_dynamic_background",
        "cardview_dark_background" to "@color/piko_dynamic_background",
        "material_grey_850" to "@color/piko_dynamic_background",
        "material_grey_900" to "@color/piko_dynamic_background",
        "igds_prism_gray_07" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_09" to "@color/piko_dynamic_pressed_background",
        "igds_prism_gray_13" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_14" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_1500" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_09_alpha_95" to "@color/piko_dynamic_prism_black",
        "igds_prism_gray_10_alpha_95" to "@color/piko_dynamic_prism_black",
        "bds_grey_9_95_transparent" to "@color/piko_dynamic_prism_black",
        "bds_grey_10_90_transparent" to "@color/piko_dynamic_prism_black",
        "gm3_baseline_surface_container" to "@color/piko_dynamic_background",
        "gm3_baseline_surface_container_light" to "@color/piko_dynamic_background",
        "baseline_neutral_100_with_surface_tint_light_alpha_12" to "@color/piko_dynamic_background",
        "material_grey_100" to "@color/piko_dynamic_background",
        // This shared surface/foreground token must follow the surface to preserve dark mode.
        "abc_decor_view_status_guard_light" to "@color/piko_dynamic_background",
        "background_floating_material_light" to "@color/piko_dynamic_background",
        "igds_prism_gray_01" to "@color/piko_dynamic_prism_white",
        "igds_prism_gray_02" to "@color/piko_dynamic_prism_white",
        "igds_prism_gray_03" to "@color/piko_dynamic_neutral_light",
    )

internal val amoledSurfaceBaselineMappings =
    materialYouSurfaceBaselineMappings
        .filterValues { value ->
            value == "@color/piko_dynamic_background" ||
                value == "@color/piko_dynamic_prism_black"
        }
        .mapValues { "@color/bds_black" }

internal val materialYouNamedMappings =
    materialYouBaseMappings +
        materialYouAccentMappings +
        materialYouThemeMappings +
        materialYouSurfaceBaselineMappings

private val amoledMaterialYouAliases =
    materialYouDarkDynamicAliases +
        materialYouNeutralConstantsDynamic +
        mapOf(
            "piko_dynamic_background" to "#ff000000",
            "piko_dynamic_prism_black" to "#ff000000",
        )

internal fun amoledMaterialYouOverlayMappings(): Map<String, String> =
    materialYouNamedMappings.mapValues { (name, value) ->
        if (name == "igds_elevated_highlight_background") {
            amoledMaterialYouAliases.getValue("piko_dynamic_pressed_background")
        } else {
            val alias = value.removePrefix("@color/")
            amoledMaterialYouAliases[alias] ?: value
        }
    }

internal val amoledSplashMappings =
    mapOf("ig_splash_screen_background" to "@color/bds_black")

internal fun dynamicOverlayMappings(night: Boolean): Map<String, String> {
    val aliases =
        if (night) {
            materialYouDarkDynamicAliases + materialYouNeutralConstantsDynamic
        } else {
            materialYouLightDynamicAliases + materialYouNeutralConstantsDynamic
        }

    return materialYouNamedMappings.mapValues { (_, value) ->
        val alias = value.removePrefix("@color/")
        aliases[alias] ?: value
    }
}
