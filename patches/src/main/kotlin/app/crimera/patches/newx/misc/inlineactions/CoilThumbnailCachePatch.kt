package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.MEDIA_THUMBNAIL_LOADER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.Method

private const val COIL_SCOPE = "Lcoil3/"
private const val MEMORY_CACHE_SCOPE = "Lcoil3/memory/"
private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"
private const val SET_DESCRIPTOR = "Ljava/util/Set;"
private const val INTEGER_DESCRIPTOR = "I"
private const val BOOLEAN_DESCRIPTOR = "Z"
private const val BITMAP_DESCRIPTOR = "Landroid/graphics/Bitmap;"
private const val ATOMIC_REFERENCE_DESCRIPTOR =
    "Ljava/util/concurrent/atomic/AtomicReference;"
private const val CACHED_THUMBNAIL_HELPER = "getCachedThumbnail"
private const val CACHED_THUMBNAIL_LOCAL_REGISTER_COUNT = 4

/** Finds Coil's process-wide image-loader factory without naming an obfuscated Coil class. */
private object CoilImageLoaderProviderFingerprint : Fingerprint(
    definingClass = COIL_SCOPE,
    parameters = listOf(CONTEXT_DESCRIPTOR),
    filters = listOf(
        methodCall(
            definingClass = ATOMIC_REFERENCE_DESCRIPTOR,
            name = "get",
            parameters = emptyList(),
            returnType = OBJECT_DESCRIPTOR,
        ),
        methodCall(
            definingClass = ATOMIC_REFERENCE_DESCRIPTOR,
            name = "compareAndSet",
            parameters = listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR),
            returnType = BOOLEAN_DESCRIPTOR,
        ),
    ),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType.toString().startsWith(COIL_SCOPE)
    },
)

/** Scopes the concrete loader getter to the implementation of the resolved loader interface. */
private fun coilImageLoaderMemoryCacheFingerprint(imageLoaderDescriptor: String) = Fingerprint(
    definingClass = COIL_SCOPE,
    parameters = emptyList(),
    custom = { method, classDef ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            classDef.interfaces.any { it.toString() == imageLoaderDescriptor } &&
            method.returnType.toString().startsWith(MEMORY_CACHE_SCOPE)
    },
)

/** Finds the stable Coil image-to-Bitmap conversion by its public image ABI. */
private fun coilBitmapConverterFingerprint(imageDescriptor: String) = Fingerprint(
    definingClass = COIL_SCOPE,
    parameters = listOf(imageDescriptor),
    returnType = BITMAP_DESCRIPTOR,
    filters = listOf(
        methodCall(smali = "$imageDescriptor->getWidth()$INTEGER_DESCRIPTOR"),
        methodCall(smali = "$imageDescriptor->getHeight()$INTEGER_DESCRIPTOR"),
    ),
    custom = { method, _ -> AccessFlags.STATIC.isSet(method.accessFlags) },
)

private data class CoilThumbnailRuntime(
    val provider: String,
    val loaderOwner: String,
    val loader: String,
    val cacheKeys: String,
    val memoryLookup: String,
    val strongCacheField: String,
    val weakCacheField: String,
    val mapBackingField: String,
    val keyDescriptor: String,
    val keyStringField: String,
    val imageField: String,
    val converter: String,
)

internal val newXCoilThumbnailCachePatch =
    bytecodePatch(default = false) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        execute {
            patchCoilThumbnailBridge(resolveCoilThumbnailRuntime())
        }
    }

context(context: BytecodePatchContext)
private fun patchCoilThumbnailBridge(runtime: CoilThumbnailRuntime) {
    val extensionClass = context.mutableClassDefBy(MEDIA_THUMBNAIL_LOADER_DESCRIPTOR)
    val placeholder = extensionClass.requireHelper(
        CACHED_THUMBNAIL_HELPER,
        listOf(OBJECT_DESCRIPTOR, STRING_DESCRIPTOR),
    )
    val currentRegisterCount = placeholder.implementation?.registerCount
        ?: throw PatchException("NewX Coil thumbnail bridge has no implementation: $placeholder")
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
            if-eqz p0, :piko_newx_cached_thumbnail_none
            if-eqz p1, :piko_newx_cached_thumbnail_none
            check-cast p0, Landroid/content/Context;
            invoke-static {p0}, ${runtime.provider}
            move-result-object v0
            check-cast v0, ${runtime.loaderOwner}
            invoke-virtual {v0}, ${runtime.loader}
            move-result-object v0
            if-eqz v0, :piko_newx_cached_thumbnail_none

            iget-object v1, v0, ${runtime.strongCacheField}
            invoke-interface {v1}, ${runtime.cacheKeys}
            move-result-object v1
            new-instance v2, Ljava/util/LinkedHashSet;
            invoke-direct {v2, v1}, Ljava/util/LinkedHashSet;-><init>(Ljava/util/Collection;)V
            iget-object v1, v0, ${runtime.weakCacheField}
            iget-object v1, v1, ${runtime.mapBackingField}
            check-cast v1, Ljava/util/LinkedHashMap;
            invoke-virtual {v1}, Ljava/util/LinkedHashMap;->keySet()Ljava/util/Set;
            move-result-object v1
            invoke-interface {v2, v1}, Ljava/util/Set;->addAll(Ljava/util/Collection;)Z
            invoke-interface {v2}, Ljava/util/Set;->iterator()Ljava/util/Iterator;
            move-result-object v1

            :piko_newx_cached_thumbnail_loop
            invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
            move-result v2
            if-eqz v2, :piko_newx_cached_thumbnail_none
            invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v2
            check-cast v2, ${runtime.keyDescriptor}
            iget-object v3, v2, ${runtime.keyStringField}
            invoke-virtual {v3, p1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
            move-result v3
            if-eqz v3, :piko_newx_cached_thumbnail_loop
            invoke-virtual {v0, v2}, ${runtime.memoryLookup}
            move-result-object v2
            if-eqz v2, :piko_newx_cached_thumbnail_loop
            iget-object v2, v2, ${runtime.imageField}
            if-eqz v2, :piko_newx_cached_thumbnail_loop
            invoke-static {v2}, ${runtime.converter}
            move-result-object v2
            if-eqz v2, :piko_newx_cached_thumbnail_none
            return-object v2

            :piko_newx_cached_thumbnail_none
            const/4 v0, 0x0
            return-object v0
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun resolveCoilThumbnailRuntime(): CoilThumbnailRuntime {
    val providerMatch = requireSingleCacheMatch(
        "Coil image-loader provider",
        CoilImageLoaderProviderFingerprint.scopedMatchAllOrNull().orEmpty(),
    )
    val provider = providerMatch.originalMethod
    val loaderMatch = requireSingleCacheMatch(
        "Coil image-loader memory-cache getter",
        coilImageLoaderMemoryCacheFingerprint(provider.returnType.toString())
            .scopedMatchAllOrNull()
            .orEmpty(),
    )
    val loader = loaderMatch.originalMethod
    val memoryCacheDescriptor = loader.returnType.toString()
    val memoryCacheClass = context.mutableClassDefBy(memoryCacheDescriptor)

    val memoryLookup = resolveMemoryLookup(memoryCacheClass)
    val keyDescriptor = memoryLookup.parameterTypes.single().toString()
    val valueDescriptor = memoryLookup.returnType.toString()
    val keyClass = context.mutableClassDefBy(keyDescriptor)
    val valueClass = context.mutableClassDefBy(valueDescriptor)

    val strongCacheField = requireSingleCacheValue(
        "Coil strong memory-cache field",
        memoryCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.hasCacheKeyAccessor(field.type.toString())
        },
    )
    val weakCacheField = requireSingleCacheValue(
        "Coil weak memory-cache field",
        memoryCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() != OBJECT_DESCRIPTOR &&
                field.toString() != strongCacheField.toString() &&
                context.hasWeakCacheBackingShape(field.type.toString())
        },
    )
    val weakCacheClass = context.mutableClassDefBy(weakCacheField.type.toString())
    val mapBackingField = requireSingleCacheValue(
        "Coil weak memory-cache backing field",
        weakCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == OBJECT_DESCRIPTOR
        },
    )
    val keyStringField = requireSingleCacheValue(
        "Coil memory-cache key string field",
        keyClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == STRING_DESCRIPTOR
        },
    )
    val imageField = requireSingleCacheValue(
        "Coil memory-cache image field",
        valueClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.isCoilImageType(field.type.toString())
        },
    )

    val strongCacheClass = context.mutableClassDefBy(strongCacheField.type.toString())
    val cacheKeys = requireSingleCacheValue(
        "Coil memory-cache key accessor",
        strongCacheClass.methods.filter { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == SET_DESCRIPTOR
        },
    )
    val converterMatch = requireSingleCacheMatch(
        "Coil image-to-Bitmap converter",
        coilBitmapConverterFingerprint(imageField.type.toString())
            .scopedMatchAllOrNull()
            .orEmpty(),
    )

    return CoilThumbnailRuntime(
        provider = provider.toString(),
        loaderOwner = loader.definingClass.toString(),
        loader = loader.toString(),
        cacheKeys = cacheKeys.toString(),
        memoryLookup = memoryLookup.toString(),
        strongCacheField = strongCacheField.toString(),
        weakCacheField = weakCacheField.toString(),
        mapBackingField = mapBackingField.toString(),
        keyDescriptor = keyDescriptor,
        keyStringField = keyStringField.toString(),
        imageField = imageField.toString(),
        converter = converterMatch.originalMethod.toString(),
    )
}

context(context: BytecodePatchContext)
private fun resolveMemoryLookup(memoryCacheClass: ClassDef): Method {
    val matches = memoryCacheClass.methods.filter { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.size == 1 &&
            context.isMemoryKeyType(method.parameterTypes.single().toString()) &&
            context.isMemoryValueType(method.returnType.toString())
    }
    return requireSingleCacheValue("Coil memory-cache lookup", matches)
}

private fun BytecodePatchContext.hasCacheKeyAccessor(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    return classDef.methods.count { method ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.isEmpty() &&
            method.returnType.toString() == SET_DESCRIPTOR
    } == 1
}

private fun BytecodePatchContext.hasWeakCacheBackingShape(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    val instanceFields = classDef.fields.filterNot { AccessFlags.STATIC.isSet(it.accessFlags) }
    return instanceFields.count { it.type.toString() == OBJECT_DESCRIPTOR } == 1 &&
        instanceFields.any { it.type.toString() == INTEGER_DESCRIPTOR }
}

private fun BytecodePatchContext.isMemoryKeyType(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    val instanceFields = classDef.fields.filterNot { AccessFlags.STATIC.isSet(it.accessFlags) }
    return instanceFields.count { it.type.toString() == STRING_DESCRIPTOR } == 1 &&
        instanceFields.any { it.type.toString() == MAP_DESCRIPTOR }
}

private fun BytecodePatchContext.isMemoryValueType(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    val instanceFields = classDef.fields.filterNot { AccessFlags.STATIC.isSet(it.accessFlags) }
    return instanceFields.count { it.type.toString() == MAP_DESCRIPTOR } == 1 &&
        instanceFields.any { field -> isCoilImageType(field.type.toString()) }
}

private fun BytecodePatchContext.isCoilImageType(descriptor: String): Boolean {
    val classDef = classDefByOrNull(descriptor) ?: return false
    return classDef.methods.any { method ->
        method.name == "getWidth" &&
            method.parameterTypes.isEmpty() &&
            method.returnType.toString() == INTEGER_DESCRIPTOR
    } && classDef.methods.any { method ->
        method.name == "getHeight" &&
            method.parameterTypes.isEmpty() &&
            method.returnType.toString() == INTEGER_DESCRIPTOR
    }
}

private fun <T> requireSingleCacheValue(label: String, values: Collection<T>): T {
    if (values.size == 1) return values.single()
    throw PatchException(
        "Expected one $label, found ${values.size}: " + values.joinToString(),
    )
}

private fun requireSingleCacheMatch(label: String, matches: Collection<Match>): Match =
    requireSingleCacheValue(label, matches)

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.requireHelper(
    name: String,
    parameters: List<String>,
): app.morphe.patcher.util.proxy.mutableTypes.MutableMethod =
    methods.singleOrNull { method ->
        method.name == name &&
            method.parameterTypes.map(CharSequence::toString) == parameters &&
            method.returnType == OBJECT_DESCRIPTOR
    } ?: throw PatchException("NewX inline helper $name was not found")
