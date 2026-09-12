package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.utils.Constants.MEDIA_THUMBNAIL_LOADER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val GLIDE_SCOPE = "Lcom/bumptech/glide/"
private const val GLIDE_ENGINE_SCOPE = "Lcom/bumptech/glide/load/engine/"
private const val COMPONENT_CALLBACKS_DESCRIPTOR = "Landroid/content/ComponentCallbacks2;"
private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val SERIALIZABLE_DESCRIPTOR = "Ljava/io/Serializable;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"
private const val SET_DESCRIPTOR = "Ljava/util/Set;"
private const val HASH_MAP_DESCRIPTOR = "Ljava/util/HashMap;"
private const val INTEGER_DESCRIPTOR = "I"
private const val BOOLEAN_DESCRIPTOR = "Z"
private const val LONG_DESCRIPTOR = "J"
private const val WEAK_REFERENCE_DESCRIPTOR = "Ljava/lang/ref/WeakReference;"
private const val REFERENCE_QUEUE_DESCRIPTOR = "Ljava/lang/ref/ReferenceQueue;"
private const val CACHED_THUMBNAIL_HELPER = "getCachedThumbnail"
private const val GLIDE_DIAGNOSTICS_HELPER = "logGlideLookupDiagnostics"
private const val BITMAP_CONVERTER_HELPER = "bitmapFromGlideResource"
private const val CACHED_THUMBNAIL_LOCAL_REGISTER_COUNT = 14

/** Resolves Glide's process-wide singleton factory from the stable library ABI. */
private object GlideProviderFingerprint : Fingerprint(
    definingClass = GLIDE_SCOPE,
    parameters = listOf(CONTEXT_DESCRIPTOR),
    custom = { method, classDef ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType.toString().startsWith(GLIDE_SCOPE) &&
            method.returnType.toString() == classDef.type &&
            classDef.interfaces.any { it.toString() == COMPONENT_CALLBACKS_DESCRIPTOR }
    },
)

internal data class GlideThumbnailRuntime(
    val provider: String,
    val engineField: String,
    val memoryCacheField: String,
    val memoryMapField: String,
    val activeResourcesField: String,
    val activeMapField: String,
    val activeEntryDescriptor: String,
    val keyDescriptor: String,
    val keyModelField: String,
    val memoryEntryDescriptor: String,
    val memoryEntryResourceField: String,
    val resourceDescriptor: String,
    val resourceInterfaceDescriptor: String,
)

context(context: BytecodePatchContext)
internal fun applyGlideThumbnailCachePatch() {
    applyGlideThumbnailCachePatch(resolveGlideThumbnailRuntime())
}

context(context: BytecodePatchContext)
internal fun applyGlideThumbnailCachePatch(runtime: GlideThumbnailRuntime) {
    patchGlideThumbnailBridge(runtime)
}

/**
 * Older targets package Glide for other surfaces but do not expose the cache
 * layout used by the media renderer. Use Coil alone when that optional shape
 * is absent; once the shape is present, the full resolver remains strict.
 */
context(context: BytecodePatchContext)
internal fun resolveGlideThumbnailRuntimeOrNull(): GlideThumbnailRuntime? {
    val providerMatches = GlideProviderFingerprint.scopedMatchAllOrNull().orEmpty()
    if (providerMatches.isEmpty()) return null

    val providerMatch = requireExactlyOne("Glide singleton provider", providerMatches)
    val glideClass = context.mutableClassDefBy(providerMatch.originalMethod.definingClass.toString())
    val engineField = requireAtMostOne(
        "Glide engine capability field",
        glideClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString().startsWith(GLIDE_ENGINE_SCOPE) &&
                context.hasGlideEngineShape(field.type.toString())
        },
    ) ?: return null
    val engineClass = context.mutableClassDefBy(engineField.type.toString())
    requireAtMostOne(
        "Glide engine cache capability lookup",
        engineClass.methods.filter { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0].toString().startsWith(GLIDE_ENGINE_SCOPE) &&
                method.parameterTypes[1].toString() == BOOLEAN_DESCRIPTOR &&
                method.parameterTypes[2].toString() == LONG_DESCRIPTOR &&
                method.returnType.toString().startsWith(GLIDE_ENGINE_SCOPE)
        },
    ) ?: return null
    requireAtMostOne(
        "Glide memory-cache capability field",
        engineClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.hasGlideMemoryCacheShape(field.type.toString())
        },
    ) ?: return null

    // Keep the established resolver as the single source of the remaining
    // resource, active-entry, and field relationship checks.
    return resolveGlideThumbnailRuntime()
}

context(context: BytecodePatchContext)
private fun patchGlideThumbnailBridge(runtime: GlideThumbnailRuntime) {
    val extensionClass = context.mutableClassDefBy(MEDIA_THUMBNAIL_LOADER_DESCRIPTOR)
    val placeholder = extensionClass.requireHelper(
        CACHED_THUMBNAIL_HELPER,
        listOf(OBJECT_DESCRIPTOR, STRING_DESCRIPTOR),
    )
    val currentRegisterCount = placeholder.implementation?.registerCount
        ?: throw PatchException("NewX Glide thumbnail bridge has no implementation: $placeholder")
    val requiredRegisterCount =
        placeholder.numberOfParameterRegisters + CACHED_THUMBNAIL_LOCAL_REGISTER_COUNT
    val helper =
        if (currentRegisterCount >= requiredRegisterCount) {
            placeholder
        } else {
            placeholder.cloneMutable(
                additionalRegisters = requiredRegisterCount - currentRegisterCount,
            ).also { expanded ->
                extensionClass.methods.remove(placeholder)
                extensionClass.methods.add(expanded)
            }
        }

    helper.addInstructions(
        0,
        """
            const/4 v5, 0x0
            const/4 v6, 0x0
            const/4 v7, 0x0
            const/4 v8, 0x0
            const/4 v9, 0x0
            if-eqz p0, :piko_newx_glide_cached_thumbnail_none
            if-eqz p1, :piko_newx_glide_cached_thumbnail_none
            check-cast p0, $CONTEXT_DESCRIPTOR
            invoke-static {p0}, ${runtime.provider}
            move-result-object v0
            if-eqz v0, :piko_newx_glide_cached_thumbnail_none
            iget-object v0, v0, ${runtime.engineField}
            if-eqz v0, :piko_newx_glide_cached_thumbnail_none

            iget-object v10, v0, ${runtime.memoryCacheField}
            if-eqz v10, :piko_newx_glide_cached_thumbnail_active_start
            iget-object v10, v10, ${runtime.memoryMapField}
            if-eqz v10, :piko_newx_glide_cached_thumbnail_active_start
            new-instance v11, Ljava/util/LinkedHashMap;
            check-cast v10, $MAP_DESCRIPTOR
            invoke-direct {v11, v10}, Ljava/util/LinkedHashMap;-><init>($MAP_DESCRIPTOR)V
            invoke-interface {v11}, Ljava/util/Map;->keySet()$SET_DESCRIPTOR
            move-result-object v10
            invoke-interface {v10}, Ljava/util/Set;->size()$INTEGER_DESCRIPTOR
            move-result v5
            invoke-interface {v10}, Ljava/util/Set;->iterator()Ljava/util/Iterator;
            move-result-object v10

            :piko_newx_glide_cached_thumbnail_memory_loop
            invoke-interface {v10}, Ljava/util/Iterator;->hasNext()$BOOLEAN_DESCRIPTOR
            move-result v12
            if-eqz v12, :piko_newx_glide_cached_thumbnail_active_start
            invoke-interface {v10}, Ljava/util/Iterator;->next()$OBJECT_DESCRIPTOR
            move-result-object v12
            check-cast v12, ${runtime.keyDescriptor}
            iget-object v13, v12, ${runtime.keyModelField}
            invoke-static {v13}, Ljava/lang/String;->valueOf($OBJECT_DESCRIPTOR)$STRING_DESCRIPTOR
            move-result-object v13
            invoke-virtual {v13, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)$BOOLEAN_DESCRIPTOR
            move-result v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_memory_loop
            add-int/lit8 v7, v7, 1
            invoke-interface {v11, v12}, Ljava/util/Map;->get($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_memory_loop
            check-cast v13, ${runtime.memoryEntryDescriptor}
            iget-object v13, v13, ${runtime.memoryEntryResourceField}
            if-eqz v13, :piko_newx_glide_cached_thumbnail_memory_loop
            check-cast v13, ${runtime.resourceInterfaceDescriptor}
            invoke-interface {v13}, ${runtime.resourceInterfaceDescriptor}->get()$OBJECT_DESCRIPTOR
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_memory_loop
            add-int/lit8 v8, v8, 1
            invoke-static {v13}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$BITMAP_CONVERTER_HELPER($OBJECT_DESCRIPTOR)Landroid/graphics/Bitmap;
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_memory_loop
            add-int/lit8 v9, v9, 1
            move-object v4, p1
            invoke-static/range {v4 .. v9}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$GLIDE_DIAGNOSTICS_HELPER($STRING_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR)V
            return-object v13

            :piko_newx_glide_cached_thumbnail_active_start
            iget-object v10, v0, ${runtime.activeResourcesField}
            if-eqz v10, :piko_newx_glide_cached_thumbnail_none
            iget-object v10, v10, ${runtime.activeMapField}
            if-eqz v10, :piko_newx_glide_cached_thumbnail_none
            new-instance v11, Ljava/util/HashMap;
            check-cast v10, $MAP_DESCRIPTOR
            invoke-direct {v11, v10}, Ljava/util/HashMap;-><init>($MAP_DESCRIPTOR)V
            invoke-interface {v11}, Ljava/util/Map;->keySet()$SET_DESCRIPTOR
            move-result-object v10
            invoke-interface {v10}, Ljava/util/Set;->size()$INTEGER_DESCRIPTOR
            move-result v6
            invoke-interface {v10}, Ljava/util/Set;->iterator()Ljava/util/Iterator;
            move-result-object v10

            :piko_newx_glide_cached_thumbnail_active_loop
            invoke-interface {v10}, Ljava/util/Iterator;->hasNext()$BOOLEAN_DESCRIPTOR
            move-result v12
            if-eqz v12, :piko_newx_glide_cached_thumbnail_none
            invoke-interface {v10}, Ljava/util/Iterator;->next()$OBJECT_DESCRIPTOR
            move-result-object v12
            check-cast v12, ${runtime.keyDescriptor}
            iget-object v13, v12, ${runtime.keyModelField}
            invoke-static {v13}, Ljava/lang/String;->valueOf($OBJECT_DESCRIPTOR)$STRING_DESCRIPTOR
            move-result-object v13
            invoke-virtual {v13, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)$BOOLEAN_DESCRIPTOR
            move-result v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_active_loop
            add-int/lit8 v7, v7, 1
            invoke-interface {v11, v12}, Ljava/util/Map;->get($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_active_loop
            check-cast v13, ${runtime.activeEntryDescriptor}
            invoke-virtual {v13}, Ljava/lang/ref/Reference;->get()$OBJECT_DESCRIPTOR
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_active_loop
            check-cast v13, ${runtime.resourceDescriptor}
            invoke-interface {v13}, ${runtime.resourceInterfaceDescriptor}->get()$OBJECT_DESCRIPTOR
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_active_loop
            add-int/lit8 v8, v8, 1
            invoke-static {v13}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$BITMAP_CONVERTER_HELPER($OBJECT_DESCRIPTOR)Landroid/graphics/Bitmap;
            move-result-object v13
            if-eqz v13, :piko_newx_glide_cached_thumbnail_active_loop
            add-int/lit8 v9, v9, 1
            move-object v4, p1
            invoke-static/range {v4 .. v9}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$GLIDE_DIAGNOSTICS_HELPER($STRING_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR)V
            return-object v13

            :piko_newx_glide_cached_thumbnail_none
            move-object v4, p1
            invoke-static/range {v4 .. v9}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$GLIDE_DIAGNOSTICS_HELPER($STRING_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR$INTEGER_DESCRIPTOR)V
            invoke-static {p0, p1}, $MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$COIL_CACHED_THUMBNAIL_HELPER($OBJECT_DESCRIPTOR$STRING_DESCRIPTOR)$OBJECT_DESCRIPTOR
            move-result-object v0
            return-object v0
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun resolveGlideThumbnailRuntime(): GlideThumbnailRuntime {
    val providerMatch = requireSingleGlideMatch(
        "Glide singleton provider",
        GlideProviderFingerprint.scopedMatchAllOrNull().orEmpty(),
    )
    val provider = providerMatch.originalMethod
    val glideClass = context.mutableClassDefBy(provider.definingClass.toString())

    val engineField = requireSingleGlideValue(
        "Glide engine field",
        glideClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString().startsWith(GLIDE_ENGINE_SCOPE) &&
                context.hasGlideEngineShape(field.type.toString())
        },
    )
    val engineClass = context.mutableClassDefBy(engineField.type.toString())
    val cacheLookup = requireSingleGlideValue(
        "Glide engine cache lookup",
        engineClass.methods.filter { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0].toString().startsWith(GLIDE_ENGINE_SCOPE) &&
                method.parameterTypes[1].toString() == BOOLEAN_DESCRIPTOR &&
                method.parameterTypes[2].toString() == LONG_DESCRIPTOR &&
                method.returnType.toString().startsWith(GLIDE_ENGINE_SCOPE)
        },
    )
    val keyDescriptor = cacheLookup.parameterTypes.first().toString()
    val resourceDescriptor = cacheLookup.returnType.toString()
    val resourceClass = context.mutableClassDefBy(resourceDescriptor)
    val resourceInterfaceDescriptor = requireSingleGlideValue(
        "Glide resource interface",
        resourceClass.interfaces
            .map(CharSequence::toString)
            .filter(context::isGlideResourceInterface),
    )
    val keyClass = context.mutableClassDefBy(keyDescriptor)
    val keyModelField = requireSingleGlideValue(
        "Glide cache-key model field",
        keyClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == OBJECT_DESCRIPTOR
        },
    )

    val memoryCacheField = requireSingleGlideValue(
        "Glide memory-cache field",
        engineClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.hasGlideMemoryCacheShape(field.type.toString())
        },
    )
    val memoryCacheClass = context.mutableClassDefBy(memoryCacheField.type.toString())
    val memoryCacheSuperclass = memoryCacheClass.superclass?.toString()
        ?: throw PatchException("Glide memory cache has no LRU-map superclass: $memoryCacheClass")
    val memoryCacheSuperclassDef = context.mutableClassDefBy(memoryCacheSuperclass)
    val memoryMapField = requireSingleGlideValue(
        "Glide memory-cache map field",
        memoryCacheSuperclassDef.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == SERIALIZABLE_DESCRIPTOR
        },
    )

    val activeResourcesField = requireSingleGlideValue(
        "Glide active-resources field",
        engineClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.hasGlideActiveResourcesShape(
                    field.type.toString(),
                    resourceInterfaceDescriptor,
                )
        },
    )
    val activeResourcesClass = context.mutableClassDefBy(activeResourcesField.type.toString())
    val activeMapField = requireSingleGlideValue(
        "Glide active-resources map field",
        activeResourcesClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == HASH_MAP_DESCRIPTOR
        },
    )

    val activeEntryDescriptor = requireSingleGlideValue(
        "Glide active-resource entry type",
        activeResourcesClass.methods
            .filter { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.size == 1
            }
            .map { method -> method.parameterTypes.single().toString() }
            .distinct()
            .filter { descriptor ->
                context.hasGlideActiveEntryShape(descriptor, resourceInterfaceDescriptor)
            },
    )

    val memoryEntryClass = context.resolveGlideMemoryEntryClass(cacheLookup)
    val memoryEntryResourceField = requireSingleGlideValue(
        "Glide memory-entry resource field",
        memoryEntryClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == OBJECT_DESCRIPTOR
        },
    )

    return GlideThumbnailRuntime(
        provider = provider.toString(),
        engineField = engineField.toString(),
        memoryCacheField = memoryCacheField.toString(),
        memoryMapField = memoryMapField.toString(),
        activeResourcesField = activeResourcesField.toString(),
        activeMapField = activeMapField.toString(),
        keyDescriptor = keyDescriptor,
        keyModelField = keyModelField.toString(),
        memoryEntryDescriptor = memoryEntryClass.type.toString(),
        memoryEntryResourceField = memoryEntryResourceField.toString(),
        activeEntryDescriptor = activeEntryDescriptor,
        resourceDescriptor = resourceDescriptor,
        resourceInterfaceDescriptor = resourceInterfaceDescriptor,
    )
}

private fun BytecodePatchContext.hasGlideEngineShape(descriptor: String): Boolean {
    val engineClass = classDefByOrNull(descriptor) ?: return false
    return engineClass.methods.count { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.size == 3 &&
            method.parameterTypes[0].toString().startsWith(GLIDE_ENGINE_SCOPE) &&
            method.parameterTypes[1].toString() == BOOLEAN_DESCRIPTOR &&
            method.parameterTypes[2].toString() == LONG_DESCRIPTOR &&
            method.returnType.toString().startsWith(GLIDE_ENGINE_SCOPE)
    } == 1
}

private fun BytecodePatchContext.isGlideResourceInterface(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    return AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
        classDef.methods.any { method ->
            method.name == "get" &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == OBJECT_DESCRIPTOR
        }
}

private fun BytecodePatchContext.hasGlideActiveEntryShape(
    descriptor: String,
    resourceInterfaceDescriptor: String,
): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    return classDef.superclass?.toString() == WEAK_REFERENCE_DESCRIPTOR &&
        classDef.fields.any { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == resourceInterfaceDescriptor
        }
}

private fun BytecodePatchContext.hasGlideActiveResourcesShape(
    descriptor: String,
    resourceInterfaceDescriptor: String,
): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    val instanceFields = classDef.fields.filterNot { AccessFlags.STATIC.isSet(it.accessFlags) }
    val activeEntryMethods = classDef.methods.filter { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.size == 1 &&
            hasGlideActiveEntryShape(
                method.parameterTypes.single().toString(),
                resourceInterfaceDescriptor,
            )
    }
    return instanceFields.count { it.type.toString() == HASH_MAP_DESCRIPTOR } == 1 &&
        instanceFields.count { it.type.toString() == REFERENCE_QUEUE_DESCRIPTOR } == 1 &&
        activeEntryMethods.size == 1
}

private fun BytecodePatchContext.resolveGlideMemoryEntryClass(cacheLookup: Method): ClassDef {
    val instructions = cacheLookup.implementation?.instructions?.toList()
        ?: throw PatchException("Glide cache lookup has no implementation: $cacheLookup")
    val candidates = instructions.withIndex().mapNotNull { (castIndex, castInstruction) ->
        if (castInstruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
        val castType = castInstruction.getReference<TypeReference>()?.type
            ?: return@mapNotNull null
        val castRegister = (castInstruction as? OneRegisterInstruction)?.registerA
            ?: return@mapNotNull null
        val moveResult = instructions.getOrNull(castIndex - 1)
            ?: return@mapNotNull null
        val removeInstruction = instructions.getOrNull(castIndex - 2)
            ?: return@mapNotNull null
        if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT ||
            (moveResult as? OneRegisterInstruction)?.registerA != castRegister ||
            removeInstruction.opcode != Opcode.INVOKE_INTERFACE
        ) {
            return@mapNotNull null
        }
        val removeReference = removeInstruction.getReference<MethodReference>()
            ?: return@mapNotNull null
        if (removeReference.definingClass != MAP_DESCRIPTOR ||
            removeReference.name != "remove" ||
            removeReference.parameterTypes.map(CharSequence::toString) != listOf(OBJECT_DESCRIPTOR) ||
            removeReference.returnType != OBJECT_DESCRIPTOR
        ) {
            return@mapNotNull null
        }
        val classDef = classDefByOrNull(castType) ?: return@mapNotNull null
        if (hasGlideMemoryEntryShape(classDef)) classDef else null
    }.distinctBy { it.type }
    return requireSingleGlideValue("Glide memory-cache entry class", candidates)
}

private fun hasGlideMemoryEntryShape(classDef: ClassDef): Boolean {
    if (classDef.superclass?.toString() != OBJECT_DESCRIPTOR) return false
    val instanceFields = classDef.fields.filterNot {
        AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val constructors = classDef.methods.filter { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_DESCRIPTOR, INTEGER_DESCRIPTOR)
    }
    return instanceFields.count { it.type.toString() == OBJECT_DESCRIPTOR } == 1 &&
        instanceFields.count { it.type.toString() == INTEGER_DESCRIPTOR } == 1 &&
        constructors.size == 1
}

context(context: BytecodePatchContext)
private fun BytecodePatchContext.hasGlideMemoryCacheShape(descriptor: String): Boolean {
    val cacheClass = this.classDefByOrNull(descriptor) ?: return false
    val superclassDescriptor = cacheClass.superclass?.toString() ?: return false
    val superclass = this.classDefByOrNull(superclassDescriptor) ?: return false
    val mapFields = superclass.fields.filter { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type.toString() == SERIALIZABLE_DESCRIPTOR
    }
    val lruPutMethods = superclass.methods.filter { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR) &&
            method.returnType.toString() == OBJECT_DESCRIPTOR
    }
    return mapFields.size == 1 && lruPutMethods.size == 1
}

private fun <T> requireSingleGlideValue(label: String, values: Collection<T>): T {
    if (values.size == 1) return values.single()
    throw PatchException("Expected one $label, found ${values.size}: " + values.joinToString())
}

private fun requireSingleGlideMatch(label: String, matches: Collection<Match>): Match =
    requireSingleGlideValue(label, matches)

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.requireHelper(
    name: String,
    parameters: List<String>,
): app.morphe.patcher.util.proxy.mutableTypes.MutableMethod =
    methods.singleOrNull { method ->
        method.name == name &&
            method.parameterTypes.map(CharSequence::toString) == parameters &&
            method.returnType == OBJECT_DESCRIPTOR
    } ?: throw PatchException("NewX inline helper $name was not found")
