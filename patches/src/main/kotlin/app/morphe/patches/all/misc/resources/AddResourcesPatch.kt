/*
 * Copyright 2025 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe patches project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the Morphe patches
 * LICENSE file: https://github.com/MorpheApp/morphe-patches/blob/main/NOTICE
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * File-Specific Exception to Section 7b:
 * -------------------------------------
 * Section 7b (Attribution Requirement) of the Morphe patches LICENSE
 * does not apply to THIS FILE. Use of this file does NOT require any
 * user-facing, in-application, or UI-visible attribution.
 *
 * For this file only, attribution under Section 7b is satisfied by
 * retaining this comment block in the source code of this file.
 *
 * Distribution and Derivative Works:
 * ----------------------------------
 * This comment block MUST be preserved in all copies, distributions,
 * and derivative works of this file, whether in source or modified
 * form.
 *
 * All other terms of the Morphe Patches LICENSE, including Section 7c
 * (Project Name Restriction) and the GPLv3 itself, remain fully
 * applicable to this file.
 */

/**
 * Piko changes:
 * - Changed locale languages
 */

package app.morphe.patches.all.misc.resources

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.inputStreamFromBundledResource
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.logging.Logger

/**
 * If any added string resources replace existing strings in the target app.
 * If this is not enabled then ARSCLib still replaces the existing strings without any issues,
 * but it's not clear if the behavior is intentional or coincidentally works even though the
 * modified XML is not valid. Apktool crashes if duplicates are present and this must be enabled.
 */
private var patchStringsReplaceExisting = false

/**
 * Set patchStringsReplaceExisting
 */
fun setPatchStringsReplaceExisting(replaceExisting: Boolean) {
    patchStringsReplaceExisting = replaceExisting
}

internal val locales = listOf(
    AppLocale("", ""), // Default English locale. Must be first.
    AppLocale("ar-rSA", "ar"),
    AppLocale("bg-rBG", "bg"),
    AppLocale("bn-rBD", "bn"),
    AppLocale("ca-rES", "ca"),
    AppLocale("cs-rCZ", "cs"),
    AppLocale("da-rDK", "da"),
    AppLocale("de-rDE", "de"),
    AppLocale("el-rGR", "el"),
    AppLocale("es-rES", "es"),
    AppLocale("fa-rIR", "fa"),
    AppLocale("fi-rFI", "fi"),
    AppLocale("fr-rFR", "fr"),
    AppLocale("gu-rIN", "gu"),
    AppLocale("hi-rIN", "hi"),
    AppLocale("hr-rHR", "hr"),
    AppLocale("hu-rHU", "hu"),
    AppLocale("in-rID", "in"),
    AppLocale("it-rIT", "it"),
    AppLocale("iw-rIL", "iw"),
    AppLocale("ja-rJP", "ja"),
    AppLocale("kn-rIN", "kn"),
    AppLocale("ko-rKR", "ko"),
    AppLocale("mr-rIN", "mr"),
    AppLocale("ms-rMY", "ms"),
    AppLocale("nb-rNO", "nb"),
    AppLocale("nl-rNL", "nl"),
    AppLocale("pl-rPL", "pl"),
    AppLocale("pt-rBR", "pt"),
    AppLocale("ro-rRO", "ro"),
    AppLocale("ru-rRU", "ru"),
    AppLocale("sk-rSK", "sk"),
    AppLocale("sr-rSP", "sr"),
    AppLocale("sv-rSE", "sv"),
    AppLocale("ta-rIN", "ta"),
    AppLocale("th-rTH", "th"),
    AppLocale("tl-rPH", "tl"),
    AppLocale("tr-rTR", "tr"),
    AppLocale("uk-rUA", "uk"),
    AppLocale("vi-rVN", "vi"),
    AppLocale("zh-rCN", "zh-rCN"),
    AppLocale("zh-rHK", "zh-rHK"),
    AppLocale("zh-rTW", "zh-rTW"),

    // Languages not on Crowdin.
    // Only used for 'Bring back Twitter' to copy translations to multiple regional variants.
    AppLocale("ar-rEH", "ar-rEH"),
    AppLocale("", "en-rGB"),
)

internal class AppLocale(
    val srcLocale: String,
    val destLocale: String,
    val isBuiltInLanguage: Boolean = true
) {
    fun isDefaultLocale() = srcLocale.isEmpty()
    fun isBaseLocale() = srcLocale.isEmpty() && destLocale.isEmpty()

    fun getSrcLocaleFolderName() = getValuesFolderName(srcLocale)
    fun getDestLocaleFolderName() = getValuesFolderName(destLocale)

    override fun toString(): String {
        return "AppLocale(srcLocale='${getSrcLocaleFolderName()}', destLocale='${getDestLocaleFolderName()}', " +
                "isBuiltInLanguage=$isBuiltInLanguage)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppLocale

        if (srcLocale != other.srcLocale) return false
        if (destLocale != other.destLocale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = srcLocale.hashCode()
        result = 31 * result + destLocale.hashCode()
        return result
    }

    companion object {
        private fun getValuesFolderName(localeName: String): String {
            val folderName = "values"

            return if (localeName.isEmpty()) {
                folderName
            } else {
                "$folderName-$localeName"
            }
        }
    }
}

private enum class BundledResourceType {
    // Add more resource XML files as needed.
    ARRAYS,
    COLORS,
    PLURALS,
    STRINGS;

    override fun toString(): String {
        return super.toString().lowercase(Locale.US)
    }
}

private val appsToInclude = mutableSetOf<String>()

/**
 * Add all resources for the given app.
 */
internal fun addAppResources(appId: String) {
    appsToInclude.add(appId)
}

internal fun createResourceDestinationDirectoryIfNeeded(
    locale: AppLocale,
    logger: Logger,
    destSubPath: String,
    destFile: File
) {
    if (!destFile.exists()) {
        if (locale.isBuiltInLanguage) {
            logger.fine {
                "Locale: $locale does not exist in provided app file: $destSubPath"
            }
        }

        destFile.parentFile?.mkdirs()
        if (!destFile.createNewFile()) throw IllegalStateException()
        destFile.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        )
    }
}

/**
 * A destination resource file that is edited as raw text. Multiple apps add resources to the
 * same file, and appending the text of all of them writes each file only once.
 */
private class DestinationFile(
    val file: File,
    val text: String
) {
    val existingKeys: Set<Pair<String, String>> by lazy {
        val keys = mutableSetOf<Pair<String, String>>()
        forEachRawResourceElement(text) { element ->
            keys.add(element.tag to element.name)
        }
        keys
    }

    val added = mutableListOf<Pair<Pair<String, String>, String>>()
    val keysToReplace = mutableSetOf<Pair<String, String>>()
}

private const val ADD_RESOURCES_THREAD_COUNT = 4

private const val RESOURCES_CLOSING_TAG = "</resources>"

/**
 * Matches a named resource element (such as string or string-array) and not
 * the unnamed child elements nested inside it. Groups: tag, name, inner text.
 */
private val RESOURCE_ELEMENT = Regex(
    "<([\\w-]+)\\s[^>]*?name=\"([^\"]*)\"[^>]*?(?:/>|>(.*?)</\\1>)",
    RegexOption.DOT_MATCHES_ALL
)

internal val addResourcesPatch = resourcePatch(
    description = "Add resources such as strings or arrays to the app."
) {
    finalize {
        val logger = Logger.getLogger(AppLocale::class.java.name)

        // Maps appId to Set of resource keys (tagName to resourceName) added for default locale.
        val appDefaultResources = ConcurrentHashMap<String, Set<Pair<String, String>>>()

        fun openDestinationFile(
            destinationFiles: MutableMap<String, DestinationFile>,
            locale: AppLocale,
            destSubPath: String
        ): DestinationFile {
            destinationFiles[destSubPath]?.let { return it }

            val destFile = this@finalize[destSubPath]
            createResourceDestinationDirectoryIfNeeded(locale, logger, destSubPath, destFile)

            return DestinationFile(destFile, destFile.readText()).also {
                destinationFiles[destSubPath] = it
            }
        }

        fun writeDestinationFile(destSubPath: String, destination: DestinationFile) {
            if (destination.added.isEmpty()) return

            var text = destination.text
            val closingIndex = text.lastIndexOf(RESOURCES_CLOSING_TAG)
            if (closingIndex < 0) throw IllegalStateException("$destSubPath has no closing resources tag")

            var head = text.substring(0, closingIndex)
            val tail = text.substring(closingIndex)

            val added = if (destination.keysToReplace.isNotEmpty() || patchStringsReplaceExisting) {
                // Keep only the last added resource of each name (resources of the next apps
                // replace earlier ones), and remove the existing resources with the same name.
                // ARSCLib doesn't check for duplicates and uses the last added,
                // but Apktool crashes if duplicates exist.
                val latest = LinkedHashMap<Pair<String, String>, String>()
                destination.added.forEach { (key, element) ->
                    latest.remove(key)
                    latest[key] = element
                }
                val toRemove = if (patchStringsReplaceExisting) {
                    latest.keys
                } else {
                    destination.keysToReplace
                }
                head = RESOURCE_ELEMENT.replace(head) { match ->
                    val key = match.groupValues[1] to match.groupValues[2]
                    if (toRemove.contains(key)) "" else match.value
                }
                latest.values.toList()
            } else {
                destination.added.map { it.second }
            }

            val builder = StringBuilder(head.trimEnd()).append('\n')
            added.forEach { builder.append("    ").append(it).append('\n') }
            builder.append(tail)

            destination.file.writeText(builder.toString())
        }

        fun addResourcesFromFile(
            destinationFiles: MutableMap<String, DestinationFile>,
            appId: String,
            locale: AppLocale,
            resourceType: BundledResourceType,
            defaultResourcesAdded: MutableSet<Pair<String, String>>?
        ) {
            val isBaseLocale = locale.isBaseLocale()
            val isDefaultLocale = locale.isDefaultLocale()
            val srcFolderName = locale.getSrcLocaleFolderName()
            val srcSubPath = "$srcFolderName/$appId/$resourceType.xml"
            val destSubPath = "res/${locale.getDestLocaleFolderName()}/$resourceType.xml"

            val srcStream = inputStreamFromBundledResource(
                "addresources", srcSubPath
            )

            if (srcStream == null) {
                // String files are expected for base locale, but other resource types and locales are optional.
                if (resourceType == BundledResourceType.STRINGS && isBaseLocale) {
                    throw IllegalArgumentException("Could not find: $srcSubPath")
                }
                return
            }

            val srcText = srcStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val destination = openDestinationFile(destinationFiles, locale, destSubPath)
            val defaultResources = appDefaultResources[appId]
            val localeStringsAdded = mutableSetOf<String>()
            val replaceExisting = appId == "twitter-bring-back" || patchStringsReplaceExisting

            forEachRawResourceElement(srcText) { rawElement ->
                val tagName = rawElement.tag
                val resourceName = rawElement.name
                val openTag = rawElement.openTag
                val inner = rawElement.inner
                var element = inner?.let { "$openTag$it</$tagName>" } ?: openTag

                if (resourceType == BundledResourceType.STRINGS && inner != null) {
                    // Check for bad text strings that will fail resource compilation.
                    val sanitized = StringResourceSanitizer.sanitizeAndroidResourceString(
                        resourceName, inner, destSubPath
                    )
                    if (inner != sanitized) {
                        element = "$openTag$sanitized</$tagName>"
                    }
                }

                if (!localeStringsAdded.add(resourceName)) {
                    logger.warning(
                        "Duplicate string resource is declared: $srcFolderName " +
                                "resource: $resourceName"
                    )
                    return@forEachRawResourceElement
                }

                val resourceKey = tagName to resourceName
                val isKeyInDestination = destination.existingKeys.contains(resourceKey)

                if (replaceExisting && isDefaultLocale && !isKeyInDestination) {
                    // Bring-back resources are overrides, not new app resources.
                    // Skip keys that do not exist in the target version so renamed
                    // resources can coexist across multiple supported app versions.
                    logger.fine {
                        "Skipping replacement resource absent from target: " +
                                "$srcFolderName resource: $resourceName"
                    }
                    return@forEachRawResourceElement
                }

                if (isBaseLocale) {
                    defaultResourcesAdded?.add(resourceKey)
                } else if (defaultResources?.contains(resourceKey) != true) {
                    logger.fine {
                        "Ignoring removed default resource for locale " +
                                "(Issue will be fixed after next Crowdin sync): " +
                                "$srcFolderName resource: $resourceName"
                    }
                    return@forEachRawResourceElement
                }

                if (replaceExisting && isKeyInDestination) {
                    destination.keysToReplace.add(resourceKey)
                }

                destination.added += resourceKey to element
            }
        }

        fun addLocaleResources(locale: AppLocale) {
            // Destination files of the locale. Multiple apps add resources to the same files.
            val destinationFiles = mutableMapOf<String, DestinationFile>()

            appsToInclude.forEach { app ->
                val defaultResourcesAdded = if (locale.isBaseLocale()) mutableSetOf<Pair<String, String>>() else null

                BundledResourceType.entries.forEach { type ->
                    addResourcesFromFile(destinationFiles, app, locale, type, defaultResourcesAdded)
                }

                if (locale.isBaseLocale() && defaultResourcesAdded != null) {
                    appDefaultResources[app] = defaultResourcesAdded
                }
            }

            destinationFiles.forEach { (destSubPath, destination) ->
                writeDestinationFile(destSubPath, destination)
            }
        }

        // The base English locale must be added first since other locales are filtered by it.
        // Each locale only touches its own destination files, so the rest can run in parallel.
        val baseLocale = locales.firstOrNull { it.isBaseLocale() }
            ?: throw IllegalStateException("Default English locale (values) not found")
        addLocaleResources(baseLocale)

        val otherLocales = locales.filter { !it.isBaseLocale() }
        val executor = Executors.newFixedThreadPool(ADD_RESOURCES_THREAD_COUNT)
        try {
            otherLocales.map { locale ->
                executor.submit { addLocaleResources(locale) }
            }.forEach { it.get() }
        } finally {
            executor.shutdown()
        }
    }
}

internal object StringResourceSanitizer {
    // Matches unescaped double quotes.
    private val UNESCAPED_DOUBLE_QUOTE = Regex("(?<!\\\\)\"")

    // Matches unescaped single or double quotes.
    private val UNESCAPED_QUOTE = Regex("(?<!\\\\)['\"]")

    /**
     * @param key String key
     * @param value Text to validate and sanitize
     * @param filePath Path to include in any exception thrown.
     * @param throwException If true, will throw an exception on problems; otherwise, sanitizes.
     * @return sanitized string
     */
    fun sanitizeAndroidResourceString(
        key: String,
        value: String,
        filePath: String? = null,
        throwException: Boolean = false
    ): String {
        val logger = Logger.getLogger(StringResourceSanitizer::class.java.name)
        var sanitized = value

        // Could check for other invalid strings, but for now just check quotes.
        if (value.startsWith('"') && value.endsWith('"')) {
            // Raw strings allow unescaped single quotes but not double quotes.
            val inner = value.substring(1, value.length - 1)
            if (UNESCAPED_DOUBLE_QUOTE.containsMatchIn(inner)) {
                val message = "$filePath String $key contains unescaped double quotes: $value"
                if (throwException) throw IllegalArgumentException(message)
                logger.warning(message)
                sanitized = "\"" + UNESCAPED_DOUBLE_QUOTE.replace(inner, "") + "\""
            }
        } else {
            if (value.contains('\n')) {
                val message = "$filePath String $key is not raw but contains newline characters: $value"
                if (throwException) throw IllegalArgumentException(message)
                logger.warning(message)
            }

            if (UNESCAPED_QUOTE.containsMatchIn(value)) {
                val message = "$filePath String $key contains unescaped quotes: $value"
                if (throwException) throw IllegalArgumentException(message)
                logger.warning(message)
                sanitized = UNESCAPED_QUOTE.replace(value, "")
            }
        }

        return sanitized
    }
}
