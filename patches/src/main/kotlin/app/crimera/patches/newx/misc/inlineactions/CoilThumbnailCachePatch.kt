package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.utils.Constants.MEDIA_THUMBNAIL_LOADER_DESCRIPTOR
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.util.cloneMutable
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
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
internal const val COIL_CACHED_THUMBNAIL_HELPER = "getCachedThumbnailCoil"
private const val CACHED_THUMBNAIL_DIAGNOSTICS_HELPER = "logCoilLookupDiagnostics"
private const val CACHED_THUMBNAIL_LOCAL_REGISTER_COUNT = 10

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

/** Scopes the concrete loader getter to the resolved loader contract or its implementation. */
private fun coilImageLoaderMemoryCacheFingerprint(imageLoaderDescriptor: String) = Fingerprint(
    definingClass = COIL_SCOPE,
    parameters = emptyList(),
    custom = { method, classDef ->
        val owner = classDef.type.toString()
        val isResolvedLoaderOwner =
            owner == imageLoaderDescriptor ||
                classDef.interfaces.any { it.toString() == imageLoaderDescriptor }

        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.isEmpty() &&
            isResolvedLoaderOwner &&
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

context(context: BytecodePatchContext)
internal fun applyCoilThumbnailCachePatch(
    helperName: String = CACHED_THUMBNAIL_HELPER,
) {
    patchCoilThumbnailBridge(resolveCoilThumbnailRuntime(), helperName)
}

context(context: BytecodePatchContext)
private fun patchCoilThumbnailBridge(
    runtime: CoilThumbnailRuntime,
    helperName: String,
) {
    val extensionClass = context.mutableClassDefBy(MEDIA_THUMBNAIL_LOADER_DESCRIPTOR)
    val placeholder = extensionClass.requireHelper(
        helperName,
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

    val diagnosticsReference =
        methodReference(
            "$MEDIA_THUMBNAIL_LOADER_DESCRIPTOR->$CACHED_THUMBNAIL_DIAGNOSTICS_HELPER" +
                "(Ljava/lang/String;IIIII)V",
        )
    val messageRegister = helper.p0Register + 1
    helper.insertHook(
        index = 0,
        // Plain insertion semantics: a branch into the stub head keeps skipping the bridge, exactly
        // as it did when this block was a smali string.
        relocateBranchTargets = false,
    ) {
        // The stub body (a plain return) stays behind the block as dead code, as before.
        val noneLabel = "piko_newx_cached_thumbnail_none"
        val loopLabel = "piko_newx_cached_thumbnail_loop"
        (5..9).forEach { register -> constInt(register, 0) }
        ifEqz(helper.p0Register, Target.Local(noneLabel))
        ifEqz(messageRegister, Target.Local(noneLabel))
        checkCast(helper.p0Register, "Landroid/content/Context;")
        invokeStatic(methodReference(runtime.provider), helper.p0Register)
        moveResult(0, OBJECT_DESCRIPTOR)
        checkCast(0, runtime.loaderOwner)
        invokeVirtual(methodReference(runtime.loader), 0)
        moveResult(0, OBJECT_DESCRIPTOR)
        ifEqz(0, Target.Local(noneLabel))

        iget(1, 0, fieldReference(runtime.strongCacheField))
        invokeInterface(methodReference(runtime.cacheKeys), 1)
        moveResult(1, OBJECT_DESCRIPTOR)
        newInstance(2, "Ljava/util/LinkedHashSet;")
        invokeDirect(methodReference("Ljava/util/LinkedHashSet;-><init>(Ljava/util/Collection;)V"), 2, 1)
        iget(1, 0, fieldReference(runtime.weakCacheField))
        iget(1, 1, fieldReference(runtime.mapBackingField))
        checkCast(1, "Ljava/util/LinkedHashMap;")
        invokeVirtual(methodReference("Ljava/util/LinkedHashMap;->keySet()Ljava/util/Set;"), 1)
        moveResult(1, OBJECT_DESCRIPTOR)
        invokeInterface(methodReference("Ljava/util/Set;->addAll(Ljava/util/Collection;)Z"), 2, 1)
        invokeInterface(methodReference("Ljava/util/Set;->size()I"), 2)
        moveResult(5, "I")
        (6..9).forEach { register -> constInt(register, 0) }
        invokeInterface(methodReference("Ljava/util/Set;->iterator()Ljava/util/Iterator;"), 2)
        moveResult(1, OBJECT_DESCRIPTOR)

        label(loopLabel)
        invokeInterface(methodReference("Ljava/util/Iterator;->hasNext()Z"), 1)
        moveResult(2, "Z")
        ifEqz(2, Target.Local(noneLabel))
        invokeInterface(methodReference("Ljava/util/Iterator;->next()Ljava/lang/Object;"), 1)
        moveResult(2, OBJECT_DESCRIPTOR)
        checkCast(2, runtime.keyDescriptor)
        iget(3, 2, fieldReference(runtime.keyStringField))
        invokeVirtual(methodReference("Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z"), 3, messageRegister)
        moveResult(3, "Z")
        ifEqz(3, Target.Local(loopLabel))
        intAddLiteral8(6, 6, 1)
        invokeVirtual(methodReference(runtime.memoryLookup), 0, 2)
        moveResult(2, OBJECT_DESCRIPTOR)
        ifEqz(2, Target.Local(loopLabel))
        intAddLiteral8(7, 7, 1)
        iget(2, 2, fieldReference(runtime.imageField))
        ifEqz(2, Target.Local(loopLabel))
        intAddLiteral8(8, 8, 1)
        invokeStatic(methodReference(runtime.converter), 2)
        moveResult(2, OBJECT_DESCRIPTOR)
        ifEqz(2, Target.Local(noneLabel))
        intAddLiteral8(9, 9, 1)
        move(4, messageRegister, OBJECT_DESCRIPTOR)
        invokeStatic(diagnosticsReference, 4, 5, 6, 7, 8, 9)
        returnObject(2)

        label(noneLabel)
        move(4, messageRegister, OBJECT_DESCRIPTOR)
        invokeStatic(diagnosticsReference, 4, 5, 6, 7, 8, 9)
        constInt(0, 0)
        returnObject(0)
    }
}

context(context: BytecodePatchContext)
private fun resolveCoilThumbnailRuntime(): CoilThumbnailRuntime {
    val providerMatch = requireExactlyOne(
        "Coil image-loader provider",
        CoilImageLoaderProviderFingerprint.scopedMatchAllOrNull().orEmpty(),
    )
    val provider = providerMatch.originalMethod
    val loaderMatch = requireExactlyOne(
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

    val strongCacheField = requireExactlyOne(
        "Coil strong memory-cache field",
        memoryCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.hasCacheKeyAccessor(field.type.toString())
        },
    )
    val weakCacheField = requireExactlyOne(
        "Coil weak memory-cache field",
        memoryCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() != OBJECT_DESCRIPTOR &&
                field.toString() != strongCacheField.toString() &&
                context.hasWeakCacheBackingShape(field.type.toString())
        },
    )
    val weakCacheClass = context.mutableClassDefBy(weakCacheField.type.toString())
    val mapBackingField = requireExactlyOne(
        "Coil weak memory-cache backing field",
        weakCacheClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == OBJECT_DESCRIPTOR
        },
    )
    val keyStringField = requireExactlyOne(
        "Coil memory-cache key string field",
        keyClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == STRING_DESCRIPTOR
        },
    )
    val imageField = requireExactlyOne(
        "Coil memory-cache image field",
        valueClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                context.isCoilImageType(field.type.toString())
        },
    )

    val strongCacheClass = context.mutableClassDefBy(strongCacheField.type.toString())
    val cacheKeys = requireExactlyOne(
        "Coil memory-cache key accessor",
        strongCacheClass.methods.filter { method ->
            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == SET_DESCRIPTOR
        },
    )
    val converterMatch = requireExactlyOne(
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
    return requireExactlyOne("Coil memory-cache lookup", matches)
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

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.requireHelper(
    name: String,
    parameters: List<String>,
): app.morphe.patcher.util.proxy.mutableTypes.MutableMethod =
    requireExactlyOne(
        "NewX inline helper $name",
        methods.filter { method ->
            method.name == name &&
                method.parameterTypes.map(CharSequence::toString) == parameters &&
                method.returnType == OBJECT_DESCRIPTOR
        },
    )
