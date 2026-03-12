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

package app.morphe.util.resource

import app.morphe.patches.all.misc.resources.StringResourceSanitizer.sanitizeAndroidResourceString
import app.morphe.patches.all.misc.resources.locales
import app.morphe.util.inputStreamFromBundledResource
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks resource strings for invalid strings that will fail resource compilation.
 */
internal fun main(args: Array<String>) {
    var stringsChecked = 0

    val exceptions = mutableListOf<Exception>()

    arrayOf(
        "shared",
        "twitter",
        "twitter-bring-back",
        // Add more apps as created.
    ).forEach { appId ->
        locales.forEach { locale ->
            val srcFolderName = locale.getSrcLocaleFolderName()
            val srcSubPath = "$srcFolderName/$appId/strings.xml"

            inputStreamFromBundledResource(
                "addresources", srcSubPath
            ).use { stream ->
                if (stream == null) throw IllegalArgumentException("Could not find resource $srcSubPath")
                val document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(stream)

                val nodeList = document.getElementsByTagName("string")
                for (i in 0 until nodeList.length) {
                    val node = nodeList.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE) {
                        val element = node as Element
                        val name = element.getAttribute("name")
                        val value = element.textContent
                        try {
                            sanitizeAndroidResourceString(
                                key = name,
                                value = value,
                                filePath = srcSubPath,
                                throwException = true
                            )
                        } catch (e: Exception) {
                            exceptions += e
                        }

                        stringsChecked++
                    }
                }
            }
        }
    }

    if (exceptions.isNotEmpty()) {
        val builder = StringBuilder("\n")
        exceptions.forEach { exception ->
            builder.appendLine(exception.message)
        }
        throw IllegalStateException(builder.toString())
    }

    println("Verified $stringsChecked strings, no issues found")
}
