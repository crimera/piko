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
 * Piko changes made:
 * - Changed locale languages
 */

package app.morphe.patches.all.misc.resources

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.StringResourceSanitizer.sanitizeAndroidResourceString
import app.morphe.util.forEachChildElement
import app.morphe.util.getNode
import app.morphe.util.inputStreamFromBundledResource
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

internal val locales = listOf(
    AppLocale("", ""), // Default English locale. Must be first.
    AppLocale("ar-rSA", "ar"),
    AppLocale("es-rES", "es"),
    AppLocale("fr-rFR", "fr"),
    AppLocale("hi-rIN", "hi"),
    AppLocale("in-rID", "in"),
    AppLocale("it-rIT", "it"),
    AppLocale("ja-rJP", "ja"),
    AppLocale("ko-rKR", "ko"),
    AppLocale("pl-rPL", "pl"),
    AppLocale("pt-rBR", "pt"),
    AppLocale("ru-rRU", "ru"),
    AppLocale("zh-rCN", "zh-rCN"),
    AppLocale("zh-rHK", "zh-rHK"),
    AppLocale("zh-rTW", "zh-rTW"),
)

internal class AppLocale(
    private val srcLocale: String,
    private val destLocale: String,
    val isBuiltInLanguage: Boolean = true
) {
    fun isDefaultLocale() = srcLocale.isEmpty()

    fun getSrcLocaleFolderName() = getValuesFolderName(srcLocale)
    fun getDestLocaleFolderName() = getValuesFolderName(destLocale)

    override fun toString(): String {
        return "AppLocale(srcLocale='${getSrcLocaleFolderName()}', destLocale='${getDestLocaleFolderName()}', " +
                "isBuiltInLanguage=$isBuiltInLanguage)"
    }

    private companion object {
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
    // Add more resource xml files as needed.
    ARRAYS,
    COLORS,
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

internal val addResourcesPatch = resourcePatch(
    description = "Add resources such as strings or arrays to the app."
) {

    val defaultResourcesAdded = mutableSetOf<String>()


    finalize {
        fun getLogger(): Logger = Logger.getLogger(AppLocale.javaClass.name)

        fun addResourcesFromFile(
            appId: String,
            locale: AppLocale,
            resourceType: BundledResourceType
        ) {
            val isDefaultLocale = locale.isDefaultLocale()
            val srcFolderName = locale.getSrcLocaleFolderName()
            val srcSubPath = "$srcFolderName/$appId/$resourceType.xml"
            val destSubPath = "res/${locale.getDestLocaleFolderName()}/$resourceType.xml"

            val srcStream = inputStreamFromBundledResource(
                "addresources", srcSubPath
            )

            if (srcStream == null) {
                // String files are expected but other resource types are optional.
                if (resourceType == BundledResourceType.STRINGS) {
                    throw IllegalArgumentException("Could not find: $srcSubPath")
                }
                return
            }

            srcStream.use {
                val destFile = this@finalize[destSubPath]
                if (!destFile.exists()) {
                    if (locale.isBuiltInLanguage) {
                        getLogger().warning {
                                    "Provided app does not contain all region localizations. " +
                                    "Locale: $locale does not exist in provided app file: $destSubPath"
                        }
                    }

                    destFile.parentFile?.mkdirs()
                    if (!destFile.createNewFile()) throw IllegalStateException()
                    destFile.writeText(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
                    )
                }

                document(destSubPath).use { destDoc ->
                    val destResourceNode = destDoc.getNode("resources")

                    document(srcStream).use { srcDoc ->
                        // Check for bad localized files with duplicate strings.
                        val localeStringsAdded = mutableSetOf<String>()

                        srcDoc.getElementsByTagName(
                            "resources"
                        ).item(0)?.forEachChildElement { srcNode ->
                            val resourceName = srcNode.getAttributeNode("name").value
                            if (resourceType == BundledResourceType.STRINGS) {
                                // Check for bad text strings that will fail resource compilation.
                                val textContent = srcNode.textContent
                                val sanitized = sanitizeAndroidResourceString(
                                    resourceName, textContent, destSubPath
                                )
                                if (textContent != sanitized) {
                                    srcNode.textContent = sanitized
                                }
                            }

                            if (!localeStringsAdded.add(resourceName)) {
                                getLogger().warning(
                                    "Duplicate string resource is declared: $srcFolderName " +
                                            "resource: $resourceName"
                                )
                                return@forEachChildElement
                            }

                            if (isDefaultLocale) {
                                // Duplicate check already handled above.
                                defaultResourcesAdded.add(resourceName)
                            } else if (!defaultResourcesAdded.contains(resourceName)) {
                                // TODO: Enable when patcher/CLI supports debug/dev logging.
                                if (false) getLogger().log(Level.INFO) {
                                    "Ignoring removed default resource for locale (Issue will be fixed after next Crowdin sync): " +
                                            "$srcFolderName resource: $resourceName"
                                }
                                return@forEachChildElement
                            }

                            // Remove existing resources with the same name.
                            // ARSCLib doesn't check for duplicates and uses the last added,
                            // but Apktool crashes if duplicates exist.
                            val srcAttrName = srcNode.getAttribute("name")
                            if (srcAttrName.isNotEmpty()) {
                                val childNodes = destResourceNode.childNodes
                                val tagName = srcNode.tagName

                                for (i in 0 until childNodes.length) {
                                    val node = childNodes.item(i)

                                    if (node != null &&
                                        node.nodeType == Node.ELEMENT_NODE &&
                                        node.nodeName == tagName &&
                                        (node as Element).getAttribute("name") == srcAttrName
                                    ) {
                                        destResourceNode.removeChild(node)
                                        break
                                    }
                                }
                            }

                            val importedSrcNode = destDoc.importNode(srcNode, true)
                            destResourceNode.appendChild(importedSrcNode)
                        }
                    }
                }
            }
        }

        appsToInclude.forEach { app ->
            locales.forEach { locale ->
                BundledResourceType.entries.forEach { type ->
                    addResourcesFromFile(app, locale, type)
                }
            }
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
