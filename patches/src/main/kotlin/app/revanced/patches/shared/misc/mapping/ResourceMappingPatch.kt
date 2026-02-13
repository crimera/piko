/*
 * Copyright 2025 Morphe.
 * https://github.com/MorpheApp/morphe-patches/blob/0736833d29ed8481e1119f7f4bb2ba70342418c5/patches/src/main/kotlin/app/morphe/patches/shared/misc/mapping/ResourceMappingPatch.kt
 *
 * File-Specific License Notice (GPLv3 Section 7 Additional Permission).
 *
 * This file is part of the Morphe patches project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the Morphe patches LICENSE file.
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

package app.revanced.patches.shared.misc.mapping

import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.LiteralFilter
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.Collections
import kotlin.collections.associateBy
import kotlin.collections.set
import kotlin.io.inputStream
import kotlin.io.use
import kotlin.ranges.until
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toLong

enum class ResourceType(val value: String) {
    ANIM("anim"),
    ANIMATOR("animator"),
    ARRAY("array"),
    ATTR("attr"),
    BOOL("bool"),
    COLOR("color"),
    DIMEN("dimen"),
    DRAWABLE("drawable"),
    FONT("font"),
    FRACTION("fraction"),
    ID("id"),
    INTEGER("integer"),
    INTERPOLATOR("interpolator"),
    LAYOUT("layout"),
    MENU("menu"),
    MIPMAP("mipmap"),
    NAVIGATION("navigation"),
    PLURALS("plurals"),
    RAW("raw"),
    STRING("string"),
    STYLE("style"),
    STYLEABLE("styleable"),
    TRANSITION("transition"),
    VALUES("values"),
    XML("xml");

    companion object {
        private val VALUE_MAP: Map<String, ResourceType> = entries.associateBy { it.value }

        fun fromValue(value: String) = VALUE_MAP[value]
            ?: throw kotlin.IllegalArgumentException("Unknown resource type: $value")
    }
}

data class ResourceElement(val type: ResourceType, val name: String, val id: Long)

private lateinit var resourceMappings: MutableMap<String, ResourceElement>

private fun setResourceId(type: ResourceType, name: String, id: Long) {
    resourceMappings[type.value + name] = ResourceElement(type, name, id)
}

/**
 * @return A resource id of the given resource type and name.
 * @throws PatchException if the resource is not found.
 */
fun getResourceId(type: ResourceType, name: String) = resourceMappings[type.value + name]?.id
    ?: throw PatchException("Could not find resource type: $type name: $name")

/**
 * @return All resource elements.  If a single resource id is needed instead use [getResourceId].
 */
fun getResourceElements() = Collections.unmodifiableCollection(resourceMappings.values)

/**
 * @return If the resource exists.
 */
fun hasResourceId(type: ResourceType, name: String) = resourceMappings[type.value + name] != null

/**
 * Identical to [LiteralFilter] except uses a decoded resource literal value.
 *
 * Any patch with fingerprints of this filter must
 * also declare [resourceMappingPatch] as a dependency.
 */
fun resourceLiteral(
    type: ResourceType,
    name: String,
    location : InstructionLocation = InstructionLocation.MatchAfterAnywhere()
) = literal({ getResourceId(type, name) }, null, location)


val resourceMappingPatch = resourcePatch {
    execute {
        // Use a stream of the file, since no modifications are done
        // and using a File parameter causes the file to be re-wrote when closed.
        document(get("res/values/public.xml").inputStream()).use { document ->
            val resources = document.documentElement.childNodes
            val resourcesLength = resources.length
            resourceMappings = kotlin.collections.HashMap(2 * resourcesLength)

            for (i in 0 until resourcesLength) {
                val node = resources.item(i) as? Element ?: continue
                if (node.nodeName != "public") continue

                val nameAttribute = node.getAttribute("name")
                if (nameAttribute.startsWith("APKTOOL")) continue

                val typeAttribute = node.getAttribute("type")
                val id = node.getAttribute("id").substring(2).toLong(16)

                setResourceId(ResourceType.fromValue(typeAttribute), nameAttribute, id)
            }
        }
    }
}
