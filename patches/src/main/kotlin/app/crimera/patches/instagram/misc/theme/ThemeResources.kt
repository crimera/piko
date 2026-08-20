/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.util.findElementByAttributeValueOrThrow
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import org.w3c.dom.Document
import org.w3c.dom.Element

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

internal fun Document.forceWhiteOnMediaChromeItems() {
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

internal fun ResourcePatchContext.preserveLightOverflowStampBackgrounds() {
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
        if (!get(path).exists()) return@forEach
        document(path).use { document ->
            stampBackgroundReplacements +=
                document.preserveLightOverflowStampBackgroundItems()
        }
    }
    check(stampBackgroundReplacements > 0) {
        "Unable to preserve light overflow stamp backgrounds"
    }
}

internal fun ResourcePatchContext.preserveCreationButtonContrast() {
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
        if (!get(path).exists()) return@forEach
        document(path).use { document ->
            replacements += document.preserveCreationButtonContrastItems()
        }
    }
    check(replacements > 0) {
        "Unable to preserve creation button contrast"
    }
}

internal fun ResourcePatchContext.preserveLightClipsComposerContrast() {
    val sourcePath = "res/drawable/clips_composer_background.xml"
    val source = get(sourcePath)
    check(source.isFile) {
        "Unable to find the clips composer background"
    }

    val targetPath = "res/drawable/$STOCK_CLIPS_COMPOSER_BACKGROUND.xml"
    source.copyTo(
        get("res/drawable").resolve("$STOCK_CLIPS_COMPOSER_BACKGROUND.xml"),
        overwrite = true,
    )
    document(targetPath).use { document ->
        val solids = document.getElementsByTagName("solid")
        check(solids.length == 1) {
            "Unexpected clips composer background shape"
        }
        val solid = solids.item(0) as? Element
            ?: error("Unable to read the clips composer background fill")
        check(solid.getAttribute("android:color") == "?attr/igds_color_secondary_background") {
            "Unexpected clips composer background fill"
        }
        // Material You remaps the source token, so keep Instagram's stock light fill.
        solid.setAttribute("android:color", STOCK_CLIPS_COMPOSER_BACKGROUND_COLOR)
    }
    document("res/values/public.xml").use { document ->
        document.ensurePublicResource("drawable", STOCK_CLIPS_COMPOSER_BACKGROUND)
    }
}

internal fun ResourcePatchContext.forceWhiteOnMediaChrome() {
    listOf(
        "res/values/styles.xml",
        "res/values-night/styles.xml",
    ).forEach { path ->
        if (!get(path).exists()) return@forEach
        document(path).use { document ->
            document.forceWhiteOnMediaChromeItems()
        }
    }
}

internal fun ResourcePatchContext.captureApi31Base(): Api31BaseSnapshot {
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

internal fun ResourcePatchContext.applyLegacyTheme(amoled: Boolean) {
    if (amoled) {
        applyLegacyAmoledTheme()
    } else {
        applyLegacyMaterialYouTheme()
    }
}

internal fun ResourcePatchContext.restoreApi31Base(
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
    val values = dynamicOverlayMappings(night) + materialYouCreationButtonMappings
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

internal fun ResourcePatchContext.writeMaterialYouOverlay(night: Boolean): File =
    buildThemeOverlayTable(
        sourceManifest = get("AndroidManifest.xml"),
        sourcePublic = get("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile =
            get(
                if (night) {
                    "assets/piko/material_you_dark.arsc"
                } else {
                    "assets/piko/material_you_light.arsc"
                },
                copy = false,
            ),
        values = materialYouOverlayValues(night),
    )

internal fun ResourcePatchContext.writeAmoledOverlay(
    snapshot: Api31BaseSnapshot,
): File =
    buildThemeOverlayTable(
        sourceManifest = get("AndroidManifest.xml"),
        sourcePublic = get("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile = get("assets/piko/amoled.arsc", copy = false),
        values = amoledOverlayValues(snapshot),
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

internal fun ResourcePatchContext.writeAmoledMaterialYouOverlay(): File =
    buildThemeOverlayTable(
        sourceManifest = get("AndroidManifest.xml"),
        sourcePublic = get("res/values/public.xml"),
        packageName = packageMetadata.packageName,
        outputFile = get("assets/piko/amoled_material_you.arsc", copy = false),
        values = amoledMaterialYouOverlayValues(),
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

private fun ResourcePatchContext.applyLegacyAmoledTheme() {
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

private fun ResourcePatchContext.applyLegacyMaterialYouTheme() {
    listOf(
        "res/values" to (materialYouLightFallbackAliases + materialYouNeutralConstantsHex),
        "res/values-night" to (materialYouDarkFallbackAliases + materialYouNeutralConstantsHex),
    ).forEach { (directoryPath, aliases) ->
        ensureColorsXml(directoryPath)
        document("$directoryPath/colors.xml").use { document ->
            (aliases + materialYouNamedMappings).forEach { (name, value) ->
                document.upsertColor(name, value)
            }
        }
    }
}

private fun ResourcePatchContext.readColors(path: String): Map<String, String> {
    if (!get(path).exists()) return emptyMap()

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
    materialYouNamedMappings.keys.associateWith { name ->
        checkNotNull(buckets.firstNotNullOfOrNull { it[name] }) {
            "Unable to resolve base color: $name"
        }
    }

private fun ResourcePatchContext.writeColors(
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
