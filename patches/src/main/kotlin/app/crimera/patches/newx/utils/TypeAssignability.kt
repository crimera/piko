package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.BytecodePatchContext

/**
 * Platform collection interfaces live on the framework classpath, not in the dex, so a concrete
 * return type (`HashSet` for `Set`, `ArrayList` for `List`) is rejected by exact descriptor
 * equality. Walk the dex hierarchy and fall back to the known platform implementations of the
 * requested interface.
 */
private val PLATFORM_IMPLEMENTATIONS =
    mapOf(
        "Ljava/util/List;" to
            setOf(
                "Ljava/util/AbstractList;",
                "Ljava/util/ArrayList;",
                "Ljava/util/LinkedList;",
                "Ljava/util/Vector;",
                "Ljava/util/Stack;",
                "Ljava/util/Collections\$UnmodifiableList;",
            ),
        "Ljava/util/Set;" to
            setOf(
                "Ljava/util/AbstractSet;",
                "Ljava/util/HashSet;",
                "Ljava/util/LinkedHashSet;",
                "Ljava/util/SortedSet;",
                "Ljava/util/NavigableSet;",
                "Ljava/util/TreeSet;",
                "Ljava/util/Collections\$UnmodifiableSet;",
            ),
        "Ljava/util/Map;" to
            setOf(
                "Ljava/util/AbstractMap;",
                "Ljava/util/HashMap;",
                "Ljava/util/LinkedHashMap;",
                "Ljava/util/SortedMap;",
                "Ljava/util/NavigableMap;",
                "Ljava/util/TreeMap;",
            ),
    )

internal fun BytecodePatchContext.isAssignableTo(descriptor: String, target: String): Boolean {
    if (descriptor == target) return true
    if (descriptor in PLATFORM_IMPLEMENTATIONS[target].orEmpty()) return true
    val classDef = classDefByOrNull(descriptor) ?: return false
    if (classDef.interfaces.any { it.toString() == target }) return true
    val superclass = classDef.superclass?.toString() ?: return false
    return isAssignableTo(superclass, target)
}
