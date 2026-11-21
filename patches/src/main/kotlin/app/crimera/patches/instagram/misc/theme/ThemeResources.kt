/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.resourceFile
import app.morphe.util.findElementByAttributeValueOrThrow
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

private const val STOCK_PRISM_OVERFLOW_STAMP_BACKGROUND =
    "piko_stock_prism_overflow_stamp_background"
private const val STOCK_LEGACY_OVERFLOW_STAMP_BACKGROUND =
    "piko_stock_legacy_overflow_stamp_background"
private const val CREATION_BUTTON_BACKGROUND =
    "piko_creation_button_background"
private const val STOCK_CREATION_BUTTON_BACKGROUND = "#ff191c1f"
private const val STOCK_CLIPS_COMPOSER_BACKGROUND =
    "piko_stock_clips_composer_background"
private const val STOCK_CLIPS_COMPOSER_BACKGROUND_COLOR = "#ff25292e"
private const val STORY_LOADING_BACKGROUND = "piko_story_loading_background"
private const val STOCK_STORY_LOADING_BACKGROUND = "#ff25292e"
// Instagram inlines or drops these aliases in some releases.
private val optionalStockColors = setOf(
    "baseline_neutral_10_with_surface_tint_dark_alpha_14",
    "abc_decor_view_status_guard_light",
    "igds_primary_background",
)

internal data class Api31BaseSnapshot(
    val dayColors: Map<String, String>,
    val nightColors: Map<String, String>,
)

private val onMediaChromeAttrs =
    setOf(
        "igds_color_primary_text_on_media",
        "igds_color_icon_on_media",
        "igds_color_primary_button_on_media",
        "muteIconPrimaryColor",
    )

private val onMediaAttrsPointingAtGuardLight =
    setOf(
        "sc_primary_icon_on_media",
        "sc_primary_text_on_media",
        "sc_always_white",
    )

// Instagram 456 shares CardView white between these previously themed surfaces and foregrounds.
private val themedWhiteSurfaceAttrs =
    setOf(
        "colorSurface",
        "igds_color_bio_pill_active_background",
        "igds_color_clips_tab_inverted_background",
        "igds_color_cta_banner_background",
        "igds_color_elevated_background",
        "igds_color_elevated_background_intent_card",
        "igds_color_meta_ai_card_background",
        "igds_color_pill_active_background",
        "igds_color_primary_background",
        "igds_color_prism_card_background",
        "igds_color_prism_pill_active_background",
        "igds_color_sticker_background",
        "sc_card_background",
        "sc_popover_background",
        "sc_surface_background",
    )

private val amoledDayOverrides =
    mapOf(
        "igds_prism_black" to "#ff000000",
    )

private val amoledNightOverrides =
    mapOf(
        "igds_secondary_background" to "@color/bds_black",
        "igds_elevated_background" to "@color/bds_black",
        "igds_prism_black" to "#ff000000",
    )

private val stockLightOverflowStampBackgrounds =
    mapOf(
        "@color/igds_prism_gray_01" to
            (STOCK_PRISM_OVERFLOW_STAMP_BACKGROUND to "#fff3f5f7"),
        "@color/bds_grey_1" to
            (STOCK_LEGACY_OVERFLOW_STAMP_BACKGROUND to "#ffefefef"),
    )

private val materialYouLightDrawableMappings =
    mapOf(
        "clips_composer_background" to "@drawable/$STOCK_CLIPS_COMPOSER_BACKGROUND",
    )

private val materialYouCreationButtonMappings =
    mapOf(
        CREATION_BUTTON_BACKGROUND to "@android:color/system_neutral2_800",
    )

private fun Document.remapSurfaceAndMediaColors() {
    val items = getElementsByTagName("item")
    for (index in 0 until items.length) {
        val item = items.item(index) as? Element ?: continue
        val name = item.getAttribute("name")
        if (name in onMediaChromeAttrs) {
            item.textContent = "@color/bds_white"
        } else if (
            name in onMediaAttrsPointingAtGuardLight &&
            item.textContent == "@color/abc_decor_view_status_guard_light"
        ) {
            item.textContent = "@color/bds_white"
        } else if (name in themedWhiteSurfaceAttrs && item.textContent == "@color/cardview_light_background") {
            item.textContent = "@color/background_floating_material_light"
        }
    }
}

internal fun Document.preserveCreationButtonContrastItems(): Int {
    var replacements = 0
    val items = getElementsByTagName("item")
    for (index in 0 until items.length) {
        val item = items.item(index) as? Element ?: continue
        if (
            item.getAttribute("name") == "igds_color_creation_button" &&
            item.textContent == "@color/igds_prism_gray_1500"
        ) {
            item.textContent = "@color/$CREATION_BUTTON_BACKGROUND"
            replacements++
        }
    }
    return replacements
}

internal fun Document.preserveLightOverflowStampBackgroundItems(): Int {
    var replacements = 0
    val items = getElementsByTagName("item")
    for (index in 0 until items.length) {
        val item = items.item(index) as? Element ?: continue
        if (item.getAttribute("name") != "igds_color_stamp_background") continue

        val (resourceName) = stockLightOverflowStampBackgrounds[item.textContent] ?: continue
        item.textContent = "@color/$resourceName"
        replacements++
    }
    return replacements
}

internal fun preserveLightOverflowStampBackgrounds() {
    ensureColorsXml("res/values")
    document("res/values/colors.xml").use { document ->
        stockLightOverflowStampBackgrounds.values.forEach { (name, value) ->
            document.upsertColor(name, value)
        }
    }

    var stampBackgroundReplacements = 0
    listOf(
        "res/values/styles.xml",
        "res/values-night/styles.xml",
    ).forEach { path ->
        if (!resourceFile(path).exists()) return@forEach
        document(path).use { document ->
            stampBackgroundReplacements +=
                document.preserveLightOverflowStampBackgroundItems()
        }
    }
    check(stampBackgroundReplacements > 0) {
        "Unable to preserve light overflow stamp backgrounds"
    }
}

internal fun preserveCreationButtonContrast() {
    ensureColorsXml("res/values")
    document("res/values/colors.xml").use { document ->
        document.upsertColor(
            CREATION_BUTTON_BACKGROUND,
            STOCK_CREATION_BUTTON_BACKGROUND,
        )
    }
    document("res/values/public.xml").use { document ->
        document.ensurePublicResource("color", CREATION_BUTTON_BACKGROUND)
    }

    var replacements = 0
    listOf(
        "res/values/styles.xml",
        "res/values-night/styles.xml",
    ).forEach { path ->
        if (!resourceFile(path).exists()) return@forEach
        document(path).use { document ->
            replacements += document.preserveCreationButtonContrastItems()
        }
    }
    check(replacements > 0) {
        "Unable to preserve creation button contrast"
    }
}

internal fun preserveLightStoryBackground() {
    // Light surfaces share this color with stories, whose controls stay white.
    writeColors("res/values", mapOf(STORY_LOADING_BACKGROUND to STOCK_STORY_LOADING_BACKGROUND))
    listOf("res/values-night", "res/values-v31", "res/values-night-v31").forEach { path ->
        writeColors(path, mapOf(STORY_LOADING_BACKGROUND to "@color/igds_prism_gray_09"))
    }
    document("res/values/public.xml").use { document ->
        document.ensurePublicResource("color", STORY_LOADING_BACKGROUND)
    }

    var replacements = 0
    listOf("res/values/styles.xml", "res/values-night/styles.xml").forEach { path ->
        if (!resourceFile(path).exists()) return@forEach
        document(path).use { document ->
            val items = document.getElementsByTagName("item")
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                if (
                    item.getAttribute("name") == "igds_color_stories_loading_background" &&
                    item.textContent == "@color/igds_prism_gray_09"
                ) {
                    item.textContent = "@color/$STORY_LOADING_BACKGROUND"
                    replacements++
                }
            }
        }
    }
    check(replacements > 0) { "Unable to preserve the light story background" }
}

internal fun preserveLightClipsComposerContrast() {
    val sourcePath = "res/drawable/clips_composer_background.xml"
    val source = resourceFile(sourcePath)
    check(source.isFile) {
        "Unable to find the clips composer background"
    }

    val targetPath = "res/drawable/$STOCK_CLIPS_COMPOSER_BACKGROUND.xml"
    source.copyTo(
        resourceFile("res/drawable").resolve("$STOCK_CLIPS_COMPOSER_BACKGROUND.xml"),
        overwrite = true,
    )
    document(targetPath).use { document ->
        val solids = document.getElementsByTagName("solid")
        check(solids.length == 1) {
            "Unexpected clips composer background shape"
        }
        val solid = solids.item(0) as? Element
            ?: error("Unable to read the clips composer background fill")
        check(solid.getAttribute("android:color") in setOf(
            "?attr/igds_color_secondary_background", "?attr/igds_color_background_secondary",
        )) {
            "Unexpected clips composer background fill"
        }
        // Material You remaps the source token, so keep Instagram's stock light fill.
        solid.setAttribute("android:color", STOCK_CLIPS_COMPOSER_BACKGROUND_COLOR)
    }
    document("res/values/public.xml").use { document ->
        document.ensurePublicResource("drawable", STOCK_CLIPS_COMPOSER_BACKGROUND)
    }
}

internal fun remapSurfaceAndMediaColors() {
    listOf(
        "res/values/styles.xml",
        "res/values-night/styles.xml",
    ).forEach { path ->
        if (!resourceFile(path).exists()) return@forEach
        document(path).use { document ->
            document.remapSurfaceAndMediaColors()
        }
    }
}

internal fun captureApi31Base(): Api31BaseSnapshot {
    val values = readColors("res/values/colors.xml")
    val nightValues = readColors("res/values-night/colors.xml")
    val api31Values = readColors("res/values-v31/colors.xml")
    val api31NightValues = readColors("res/values-night-v31/colors.xml")

    return Api31BaseSnapshot(
        dayColors = resolveBaseColors(api31Values, values),
        nightColors = resolveBaseColors(
            api31NightValues,
            nightValues,
            api31Values,
            values,
        ),
    )
}

internal fun applyLegacyTheme(amoled: Boolean) {
    if (amoled) {
        applyLegacyAmoledTheme()
    } else {
        applyLegacyMaterialYouTheme()
    }
}

internal fun restoreApi31Base(
    snapshot: Api31BaseSnapshot,
) {
    writeColors(
        directoryPath = "res/values-v31",
        values = snapshot.dayColors,
    )
    writeColors(
        directoryPath = "res/values-night-v31",
        values = snapshot.nightColors,
    )
}

internal fun materialYouOverlayValues(night: Boolean): OverlayValues {
    val values = dynamicOverlayMappings(night) + materialYouCreationButtonMappings +
        if (night) emptyMap() else mapOf(STORY_LOADING_BACKGROUND to STOCK_STORY_LOADING_BACKGROUND)
    val api34Values = values + materialYouSwitchApi34Mappings(night)
    val drawableMappings =
        if (night) emptyMap() else materialYouLightDrawableMappings
    return OverlayValues(
        day = values,
        night = values,
        dayApi34 = api34Values,
        nightApi34 = api34Values,
        dayDrawables = drawableMappings,
        nightDrawables = drawableMappings,
    )
}

internal fun writeMaterialYouOverlay(night: Boolean): File =
    buildThemeOverlayTable(
        sourceManifest = resourceFile("AndroidManifest.xml"),
        sourcePublic = resourceFile("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile =
            resourceFile(
                if (night) {
                    "assets/piko/material_you_dark.arsc"
                } else {
                    "assets/piko/material_you_light.arsc"
                },
                copy = false,
            ),
        values = supportedOverlayValues(materialYouOverlayValues(night)),
    )

internal fun writeAmoledOverlay(
    snapshot: Api31BaseSnapshot,
): File =
    buildThemeOverlayTable(
        sourceManifest = resourceFile("AndroidManifest.xml"),
        sourcePublic = resourceFile("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile = resourceFile("assets/piko/amoled.arsc", copy = false),
        values = supportedOverlayValues(amoledOverlayValues(snapshot)),
    )

internal fun amoledMaterialYouOverlayValues(): OverlayValues {
    val values =
        amoledMaterialYouOverlayMappings() +
            amoledSplashMappings +
            materialYouCreationButtonMappings
    val api34Values = values + materialYouSwitchApi34Mappings(night = true)
    return OverlayValues(
        day = values,
        night = values,
        dayApi34 = api34Values,
        nightApi34 = api34Values,
    )
}

private fun materialYouSwitchApi34Mappings(night: Boolean): Map<String, String> =
    if (night) {
        mapOf(
            "material_selected_track" to "@android:color/system_primary_dark",
            "checkbox_image_tint" to "@android:color/system_on_primary_dark",
            "material_unselected_track" to
                "@android:color/system_surface_container_highest_dark",
            "material_track_border" to "@android:color/system_outline_dark",
            "checkbox_unchecked_enabled" to "@android:color/system_outline_dark",
        )
    } else {
        mapOf(
            "material_selected_track" to "@android:color/system_primary_light",
            "checkbox_image_tint" to "@android:color/system_on_primary_light",
            "material_unselected_track" to
                "@android:color/system_surface_container_highest_light",
            "material_track_border" to "@android:color/system_outline_light",
            "checkbox_unchecked_enabled" to "@android:color/system_outline_light",
        )
    }

internal fun writeAmoledMaterialYouOverlay(): File =
    buildThemeOverlayTable(
        sourceManifest = resourceFile("AndroidManifest.xml"),
        sourcePublic = resourceFile("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile = resourceFile("assets/piko/amoled_material_you.arsc", copy = false),
        values = supportedOverlayValues(amoledMaterialYouOverlayValues()),
    )

internal fun amoledOverlayValues(snapshot: Api31BaseSnapshot): OverlayValues =
    (
        snapshot.nightColors +
            amoledSurfaceBaselineMappings +
            amoledNightOverrides +
            amoledSplashMappings +
            amoledSwitchMappings
    ).let { values ->
        OverlayValues(day = values, night = values)
    }

private fun applyLegacyAmoledTheme() {
    document("res/values-night/colors.xml").use { document ->
        val colors = document.getElementsByTagName("color")
        amoledNightOverrides
            .filterKeys { it != "igds_prism_black" }
            .forEach { (name, value) ->
                colors.findElementByAttributeValueOrThrow("name", name).textContent = value
            }
    }

    document("res/values/colors.xml").use { document ->
        val colors = document.getElementsByTagName("color")
        amoledDayOverrides.forEach { (name, value) ->
            colors.findElementByAttributeValueOrThrow("name", name).textContent = value
        }
    }
}

private fun applyLegacyMaterialYouTheme() {
    val namedMappings = materialYouNamedMappings - missingOptionalStockColors()
    listOf(
        "res/values" to (materialYouLightFallbackAliases + materialYouNeutralConstantsHex),
        "res/values-night" to (materialYouDarkFallbackAliases + materialYouNeutralConstantsHex),
    ).forEach { (directoryPath, aliases) ->
        ensureColorsXml(directoryPath)
        document("$directoryPath/colors.xml").use { document ->
            (aliases + namedMappings).forEach { (name, value) ->
                document.upsertColor(name, value)
            }
        }
    }
}

private fun readColors(path: String): Map<String, String> {
    if (!resourceFile(path).exists()) return emptyMap()

    return document(path).use { document ->
        buildMap {
            val colors = document.getElementsByTagName("color")
            for (index in 0 until colors.length) {
                val color = colors.item(index) as? Element ?: continue
                put(color.getAttribute("name"), color.textContent)
            }
        }
    }
}

private fun resolveBaseColors(
    vararg buckets: Map<String, String>,
): Map<String, String> =
    materialYouNamedMappings.keys.mapNotNull { name ->
        val value = buckets.firstNotNullOfOrNull { it[name] }
        if (value == null && name in optionalStockColors) {
            null
        } else {
            name to checkNotNull(value) { "Unable to resolve base color: $name" }
        }
    }.toMap()

private fun missingOptionalStockColors(): Set<String> = optionalStockColors - readColors("res/values/colors.xml").keys

private fun supportedOverlayValues(values: OverlayValues): OverlayValues {
    val missing = missingOptionalStockColors()

    return values.copy(
        day = values.day - missing,
        night = values.night - missing,
        dayApi34 = values.dayApi34?.minus(missing),
        nightApi34 = values.nightApi34?.minus(missing),
    )
}

private fun writeColors(
    directoryPath: String,
    values: Map<String, String>,
) {
    ensureColorsXml(directoryPath)
    document("$directoryPath/colors.xml").use { document ->
        values.forEach { (name, value) ->
            document.upsertColor(name, value)
        }
    }
}

private fun ensureColorsXml(directoryPath: String) {
    val directory = resourceFile(directoryPath)
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

private fun Document.ensurePublicResource(type: String, name: String) {
    val publicNodes = getElementsByTagName("public")
    val typeIds = mutableListOf<Long>()
    for (index in 0 until publicNodes.length) {
        val resource = publicNodes.item(index) as? Element ?: continue
        if (resource.getAttribute("name") == name) {
            check(resource.getAttribute("type") == type) {
                "Resource $name already exists with another type"
            }
            return
        }
        if (resource.getAttribute("type") == type) {
            typeIds += resource.getAttribute("id").removePrefix("0x").toLong(16)
        }
    }

    check(typeIds.isNotEmpty()) {
        "Unable to allocate public $type resource $name"
    }
    val typePrefix = typeIds.first() and 0xffff0000L
    check(typeIds.all { id -> id and 0xffff0000L == typePrefix }) {
        "Unexpected public resource type IDs for $type"
    }
    val newId = typeIds.max() + 1L
    check(newId and 0xffff0000L == typePrefix) {
        "No public resource IDs remain for $type"
    }

    documentElement.appendChild(
        createElement("public").apply {
            setAttribute("type", type)
            setAttribute("name", name)
            setAttribute("id", "0x%08x".format(newId))
        },
    )
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
