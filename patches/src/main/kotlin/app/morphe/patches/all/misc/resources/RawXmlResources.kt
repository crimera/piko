/*
 * Copyright 2025 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * Section 7b: Notice Preservation
 * -------------------------------
 * This entire comment block must be preserved in all copies,
 * distributions, and derivative works of this file, in both
 * original and modified source forms.
 *
 * Portions of this software are provided "AS IS" by the Morphe software project.
 * Any express or implied warranties, including the implied warranties of
 * merchantability and fitness for a particular purpose, are disclaimed.
 */

package app.morphe.patches.all.misc.resources

/**
 * A named top level element of a resource XML file, such as a string, color or style.
 *
 * @param start Index of the opening tag.
 * @param innerStart Index after the opening tag.
 * @param innerEnd Index of the closing tag, or -1 if the element is self closing.
 * @param end Index after the element.
 */
internal class RawResourceElement(
    private val text: String,
    val tag: String,
    val name: String,
    val start: Int,
    val innerStart: Int,
    val innerEnd: Int,
    val end: Int
) {
    /** The opening tag including its attributes. */
    val openTag get() = text.substring(start, innerStart)

    /** The raw text between the tags, or null if the element is self closing. */
    val inner get() = if (innerEnd < 0) null else text.substring(innerStart, innerEnd)
}

/**
 * Calls [action] for each named top level element of a resource XML file. Comments, the
 * xml declaration, and the resources tag are skipped.
 *
 * Reading and editing the raw text is much faster than parsing a DOM,
 * especially on Android where XML parsing is slow.
 */
internal inline fun forEachRawResourceElement(
    text: String,
    action: (RawResourceElement) -> Unit
) {
    var pos = 0
    while (true) {
        val start = text.indexOf('<', pos)
        if (start < 0) return

        if (text.startsWith("<!--", start)) {
            pos = text.indexOf("-->", start + 4) + 3
            continue
        }

        val openEnd = text.indexOf('>', start)
        pos = openEnd + 1

        val nameStart = text.indexOf(" name=\"", start)
        if (text[start + 1] == '/' || text[start + 1] == '?' || nameStart < 0 || nameStart > openEnd) {
            // Closing tag, xml declaration, or a tag without a name such as the resources tag.
            continue
        }

        var tagEnd = start + 1
        while (text[tagEnd] > ' ' && text[tagEnd] != '>' && text[tagEnd] != '/') tagEnd++
        val tag = text.substring(start + 1, tagEnd)

        val nameValueStart = nameStart + 7
        val name = text.substring(nameValueStart, text.indexOf('"', nameValueStart))

        if (text[openEnd - 1] == '/') {
            action(RawResourceElement(text, tag, name, start, pos, -1, pos))
        } else {
            val closeStart = text.indexOf("</$tag>", openEnd)
            if (closeStart < 0) throw IllegalStateException("Missing closing tag for: $tag $name")
            val end = closeStart + tag.length + 3
            pos = end
            action(RawResourceElement(text, tag, name, start, openEnd + 1, closeStart, end))
        }
    }
}

/**
 * Replaces the inner text of named elements.
 *
 * @param transform Returns the new inner text of an element, or null to keep the element as is.
 */
internal fun String.transformRawResourceElements(
    transform: (element: RawResourceElement) -> String?
): String {
    var builder: StringBuilder? = null
    var copied = 0

    forEachRawResourceElement(this) { element ->
        if (element.innerEnd < 0) return@forEachRawResourceElement

        val newInner = transform(element) ?: return@forEachRawResourceElement
        if (newInner == element.inner) return@forEachRawResourceElement

        val result = builder ?: StringBuilder(length).also { builder = it }
        result.append(this, copied, element.innerStart).append(newInner)
        copied = element.innerEnd
    }

    return builder?.append(this, copied, length)?.toString() ?: this
}

/**
 * Appends elements to the end of the resources.
 */
internal fun String.appendResourceElements(elements: String): String {
    val closingIndex = lastIndexOf("</resources>")
    if (closingIndex < 0) throw IllegalStateException("No closing resources tag")

    return substring(0, closingIndex).trimEnd() + "\n" + elements + substring(closingIndex)
}
