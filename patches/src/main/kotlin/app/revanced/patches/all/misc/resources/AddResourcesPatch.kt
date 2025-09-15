package app.revanced.patches.all.misc.resources

import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.util.Document
import app.revanced.util.*
import app.revanced.util.resource.ArrayResource
import app.revanced.util.resource.BaseResource
import app.revanced.util.resource.StringResource
import org.w3c.dom.Node

/**
 * An identifier of an app. For example, `youtube`.
 */
private typealias AppId = String

/**
 * An identifier of a patch. For example, `ad.general.HideAdsPatch`.
 */
private typealias PatchId = String

/**
 * A set of resources of a patch.
 */
private typealias PatchResources = MutableSet<BaseResource>

/**
 * A map of resources belonging to a patch.
 */
private typealias AppResources = MutableMap<PatchId, PatchResources>

/**
 * A map of resources belonging to an app.
 */
private typealias Resources = MutableMap<AppId, AppResources>

/**
 * The value of a resource.
 * For example, `values` or `values-de`.
 */
private typealias Value = String

/**
 * A set of resources mapped by their value.
 */
private typealias MutableResources = MutableMap<Value, MutableSet<BaseResource>>

/**
 * A map of all resources associated by their value staged by [addResourcesPatch].
 */
private lateinit var stagedResources: Map<Value, Resources>

/**
 * A map of all resources added to the app by [addResourcesPatch].
 */
private val resources: MutableResources = mutableMapOf()

/**
 * Map of Crowdin locales to Android resource locale names.
 *
 * Fixme: Instead this patch should detect what locale regions are present in both patches and the target app,
 * and automatically merge into the appropriate existing target file.
 * So if a target app has only 'es', then the Crowdin file of 'es-rES' should merge into that.
 * But if a target app has specific regions (such as 'pt-rBR'),
 * then the Crowdin region specific file should merged into that.
 */
private val locales = mapOf(
    "af-rZA" to "af",
    "am-rET" to "am",
    "ar-rSA" to "ar",
    "as-rIN" to "as",
    "az-rAZ" to "az",
    "be-rBY" to "be",
    "bg-rBG" to "bg",
    "bn-rBD" to "bn",
    "bs-rBA" to "bs",
    "ca-rES" to "ca",
    "cs-rCZ" to "cs",
    "da-rDK" to "da",
    "de-rDE" to "de",
    "el-rGR" to "el",
    "es-rES" to "es",
    "et-rEE" to "et",
    "eu-rES" to "eu",
    "fa-rIR" to "fa",
    "fi-rFI" to "fi",
    "fil-rPH" to "tl",
    "fr-rFR" to "fr",
    "ga-rIE" to "ga",
    "gl-rES" to "gl",
    "gu-rIN" to "gu",
    "hi-rIN" to "hi",
    "hr-rHR" to "hr",
    "hu-rHU" to "hu",
    "hy-rAM" to "hy",
    "in-rID" to "in",
    "is-rIS" to "is",
    "it-rIT" to "it",
    "iw-rIL" to "iw",
    "ja-rJP" to "ja",
    "ka-rGE" to "ka",
    "kk-rKZ" to "kk",
    "km-rKH" to "km",
    "kn-rIN" to "kn",
    "ko-rKR" to "ko",
    "ky-rKG" to "ky",
    "lo-rLA" to "lo",
    "lt-rLT" to "lt",
    "lv-rLV" to "lv",
    "mk-rMK" to "mk",
    "ml-rIN" to "ml",
    "mn-rMN" to "mn",
    "mr-rIN" to "mr",
    "ms-rMY" to "ms",
    "my-rMM" to "my",
    "nb-rNO" to "nb",
    "ne-rIN" to "ne",
    "nl-rNL" to "nl",
    "or-rIN" to "or",
    "pa-rIN" to "pa",
    "pl-rPL" to "pl",
    "pt-rBR" to "pt-rBR",
    "pt-rPT" to "pt-rPT",
    "ro-rRO" to "ro",
    "ru-rRU" to "ru",
    "si-rLK" to "si",
    "sk-rSK" to "sk",
    "sl-rSI" to "sl",
    "sq-rAL" to "sq",
    "sr-rCS" to "b+sr+Latn",
    "sr-rSP" to "sr",
    "sv-rSE" to "sv",
    "sw-rKE" to "sw",
    "ta-rIN" to "ta",
    "te-rIN" to "te",
    "th-rTH" to "th",
    "tl-rPH" to "tl",
    "tr-rTR" to "tr",
    "uk-rUA" to "uk",
    "ur-rIN" to "ur",
    "uz-rUZ" to "uz",
    "vi-rVN" to "vi",
    "zh-rCN" to "zh-rCN",
    "zh-rTW" to "zh-rTW",
    "zu-rZA" to "zu",
)

/**
 * Adds a [BaseResource] to the map using [MutableMap.getOrPut].
 *
 * @param value The value of the resource. For example, `values` or `values-de`.
 * @param resource The resource to add.
 *
 * @return True if the resource was added, false if it already existed.
 */
fun addResource(
    value: Value,
    resource: BaseResource,
) = resources.getOrPut(value, ::mutableSetOf).add(resource)

/**
 * Adds a list of [BaseResource]s to the map using [MutableMap.getOrPut].
 *
 * @param value The value of the resource. For example, `values` or `values-de`.
 * @param resources The resources to add.
 *
 * @return True if the resources were added, false if they already existed.
 */
fun addResources(
    value: Value,
    resources: Iterable<BaseResource>,
) = app.revanced.patches.all.misc.resources.resources.getOrPut(value, ::mutableSetOf).addAll(resources)

/**
 * Adds a [StringResource].
 *
 * @param name The name of the string resource.
 * @param value The value of the string resource.
 * @param formatted Whether the string resource is formatted. Defaults to `true`.
 * @param resourceValue The value of the resource. For example, `values` or `values-de`.
 *
 * @return True if the resource was added, false if it already existed.
 */
fun addResources(
    name: String,
    value: String,
    formatted: Boolean = true,
    resourceValue: Value = "values",
) = addResource(resourceValue, StringResource(name, value, formatted))

/**
 * Adds an [ArrayResource].
 *
 * @param name The name of the array resource.
 * @param items The items of the array resource.
 *
 * @return True if the resource was added, false if it already existed.
 */
fun addResources(
    name: String,
    items: List<String>,
) = addResource("values", ArrayResource(name, items))

/**
 * Puts all resources of any [Value] staged in [stagedResources] for the [Patch] to [addResources].
 *
 * @param patch The [Patch] of the patch to stage resources for.
 * @param parseIds A function that parses a set of [PatchId] each mapped to an [AppId] from the given [Patch].
 * This is used to access the resources in [addResources] to stage them in [stagedResources].
 * The default implementation assumes that the [Patch] has a name and declares packages it is compatible with.
 *
 * @return True if any resources were added, false if none were added.
 *
 * @see addResourcesPatch
 */
fun addResources(
    patch: Patch<*>,
    parseIds: (Patch<*>) -> Map<AppId, Set<PatchId>> = {
        val patchId = patch.name ?: throw PatchException("Patch has no name")
        val packages = patch.compatiblePackages ?: throw PatchException("Patch has no compatible packages")

        buildMap<AppId, MutableSet<PatchId>> {
            packages.forEach { (appId, _) ->
                getOrPut(appId) { mutableSetOf() }.add(patchId)
            }
        }
    },
): Boolean {
    var result = false

    // Stage resources for the given patch to addResourcesPatch associated with their value.
    parseIds(patch).forEach { (appId, patchIds) ->
        patchIds.forEach { patchId ->
            stagedResources.forEach { (value, resources) ->
                resources[appId]?.get(patchId)?.let { patchResources ->
                    if (addResources(value, patchResources)) result = true
                }
            }
        }
    }

    return result
}

/**
 * Puts all resources for the given [appId] and [patchId] staged in [addResources] to [addResourcesPatch].
 *
 *
 * @return True if any resources were added, false if none were added.
 *
 * @see addResourcesPatch
 */
fun addResources(
    appId: AppId,
    patchId: String,
) = stagedResources.forEach { (value, resources) ->
    resources[appId]?.get(patchId)?.let { patchResources ->
        addResources(value, patchResources)
    }
}

val addResourcesPatch = resourcePatch(
    description = "Add resources such as strings or arrays to the app.",
) {
    /*
    The strategy of this patch is to stage resources present in `/resources/addresources`.
    These resources are organized by their respective value and patch.

    On addResourcesPatch#execute, all resources are staged in a temporary map.
    After that, other patches that depend on addResourcesPatch can call
    addResourcesPatch#invoke(Patch) to stage resources belonging to that patch
    from the temporary map to addResourcesPatch.

    After all patches that depend on addResourcesPatch have been executed,
    addResourcesPatch#finalize is finally called to add all staged resources to the app.
     */
    execute {
        stagedResources = buildMap {
            /**
             * Puts resources under `/resources/addresources/<value>/<resourceKind>.xml` into the map.
             *
             * @param sourceValue The source value of the resource. For example, `values` or `values-de-rDE`.
             * @param destValue The destination value of the resource. For example, 'values' or 'values-de'.
             * @param resourceKind The kind of the resource. For example, `strings` or `arrays`.
             * @param transform A function that transforms the [Node]s from the XML files to a [BaseResource].
             */
            fun addResources(
                sourceValue: Value,
                destValue: Value = sourceValue,
                resourceKind: String,
                transform: (Node) -> BaseResource,
            ) {
                inputStreamFromBundledResource(
                    "addresources",
                    "$sourceValue/$resourceKind.xml",
                )?.let { stream ->
                    // Add the resources associated with the given value to the map,
                    // instead of overwriting it.
                    // This covers the example case such as adding strings and arrays of the same value.
                    getOrPut(destValue, ::mutableMapOf).apply {
                        document(stream).use { document ->
                            document.getElementsByTagName("app").asSequence().forEach { app ->
                                val appId = app.attributes.getNamedItem("id").textContent

                                getOrPut(appId, ::mutableMapOf).apply {
                                    app.forEachChildElement { patch ->
                                        val patchId = patch.attributes.getNamedItem("id").textContent

                                        getOrPut(patchId, ::mutableSetOf).apply {
                                            patch.forEachChildElement { resourceNode ->
                                                val resource = transform(resourceNode)

                                                add(resource)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Stage all resources to a temporary map.
            // Staged resources consumed by addResourcesPatch#invoke(Patch)
            // are later used in addResourcesPatch#finalize.
            try {
                val addStringResources = { source: Value, dest: Value ->
                    addResources(source, dest, "strings", StringResource::fromNode)
                }
                locales.forEach { (source, dest) -> addStringResources("values-$source", "values-$dest") }
                addStringResources("values", "values")

                addResources("values", "values", "arrays", ArrayResource::fromNode)
            } catch (e: Exception) {
                throw PatchException("Failed to read resources", e)
            }
        }
    }

    /**
     * Adds all resources staged in [addResourcesPatch] to the app.
     * This is called after all patches that depend on [addResourcesPatch] have been executed.
     */
    finalize {
        operator fun MutableMap<String, Pair<Document, Node>>.invoke(
            value: Value,
            resource: BaseResource,
        ) {
            // TODO: Fix open-closed principle violation by modifying BaseResource#serialize so that it accepts
            //  a Value and the map of documents. It will then get or put the document suitable for its resource type
            //  to serialize itself to it.
            val resourceFileName =
                when (resource) {
                    is StringResource -> "strings"
                    is ArrayResource -> "arrays"
                    else -> throw NotImplementedError("Unsupported resource type")
                }

            getOrPut(resourceFileName) {
                this@finalize["res/$value/$resourceFileName.xml"].also {
                    it.parentFile?.mkdirs()

                    if (it.createNewFile()) {
                        it.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>")
                    }
                }
 
                document("res/$value/$resourceFileName.xml").let { document ->

                    // Save the target node here as well
                    // in order to avoid having to call document.getNode("resources")
                    // but also save the document so that it can be closed later.
                    document to document.getNode("resources")
                }
            }.let { (_, targetNode) ->
                targetNode.addResource(resource) { invoke(value, it) }
            }
        }

        resources.forEach { (value, resources) ->
            // A map of document associated by their kind (e.g. strings, arrays).
            // Each document is accompanied by the target node to which resources are added.
            // A map is used because Map#getOrPut allows opening a new document for the duration of a resource value.
            // This is done to prevent having to open the files for every resource that is added.
            // Instead, it is cached once and reused for resources of the same value.
            // This map is later accessed to close all documents for the current resource value.
            val documents = mutableMapOf<String, Pair<Document, Node>>()

            resources.forEach { resource -> documents(value, resource) }

            documents.values.forEach { (document, _) -> document.close() }
        }
    }
}
