package app.crimera.patches.newx.settings

import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.Constants.PIKO_SETTINGS_ICON_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

/** Resource copied by [newXSettingsResourcePatch]; the extension resolves its id at runtime. */
internal const val PIKO_SETTINGS_ICON_DRAWABLE = "piko_ic_vector_settings_stroke"

/**
 * Returns `PikoSettingsIcon.get()`, a cached accessor that wraps the Piko settings drawable in the
 * app's own icon type. Rows that used to `sget` the app's settings icon call it instead, so each
 * call site swaps one instruction for an invoke and a `move-result-object` without a scratch
 * register. Patch-added resources have no id until the APK is rebuilt, which is why the icon
 * cannot be a patch-time constant like the app's own icon fields.
 *
 * The accessor is generated once per [iconType]; a second caller asking for a different type
 * fails, because the settings row and drawer row are expected to share the app's icon type.
 */
context(context: BytecodePatchContext)
internal fun pikoSettingsIconGetter(iconType: String): MethodReference {
    val owner = context.mutableClassDefBy(PIKO_SETTINGS_ICON_DESCRIPTOR)
    requireAtMostOne(
        label = "NewX Piko settings icon accessor",
        candidates =
            owner.methods.filter { method ->
                method.name == GETTER_NAME && method.parameterTypes.isEmpty()
            },
    )?.let { existing ->
        if (existing.returnType != iconType) {
            throw PatchException(
                "NewX Piko settings icon accessor already returns ${existing.returnType}, " +
                    "but $iconType was requested",
            )
        }
        return existing
    }

    val constructor = resolveDrawableIconConstructor(iconType)
    val cacheField =
        ImmutableField(
            owner.type,
            CACHE_FIELD_NAME,
            iconType,
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value or AccessFlags.VOLATILE.value or
                AccessFlags.SYNTHETIC.value,
            null,
            emptySet(),
            emptySet(),
        ).toMutable()
    owner.fields.add(cacheField)

    val placeholder =
        MethodImplementationBuilder(2).apply {
            addInstruction("return-void".toInstruction())
        }.methodImplementation
    val getter =
        MutableMethod(
            ImmutableMethod(
                owner.type,
                GETTER_NAME,
                emptyList(),
                iconType,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
                emptySet(),
                emptySet(),
                placeholder,
            ),
        )
    owner.methods.add(getter)
    val implementation =
        getter.implementation
            ?: throw PatchException("NewX Piko settings icon accessor has no implementation")
    implementation.removeInstruction(implementation.instructions.lastIndex)
    getter.insertHook(0, relocateBranchTargets = false) {
        // A racing first call builds two equal icons (the type is a data class keyed on the
        // drawable id), so the unsynchronized cache is safe.
        sget(0, cacheField)
        ifNez(0, Target.Local(CACHED_LABEL))
        invokeStatic(methodReference(GET_DRAWABLE_ID_DESCRIPTOR))
        moveResult(1, "I")
        newInstance(0, iconType)
        invokeDirect(constructor, 0, 1)
        sput(0, cacheField)
        label(CACHED_LABEL)
        returnObject(0)
    }
    return getter
}

/**
 * The app builds every icon field in `<clinit>` with the drawable-id constructor (see
 * `resolveIconDrawableMap`), so the same constructor wraps the Piko drawable.
 */
context(context: BytecodePatchContext)
private fun resolveDrawableIconConstructor(iconType: String): MethodReference {
    val iconClass =
        context.classDefByOrNull(iconType)
            ?: throw PatchException("NewX icon type was not found: $iconType")
    val constructor =
        requireExactlyOne(
            label = "NewX drawable-id icon constructor for $iconType",
            candidates =
                iconClass.methods.filter { method ->
                    method.name == "<init>" &&
                        method.returnType == "V" &&
                        method.parameterTypes.map(CharSequence::toString) == listOf("I")
                },
        )
    return ImmutableMethodReference(
        constructor.definingClass,
        constructor.name,
        constructor.parameterTypes,
        constructor.returnType,
    )
}

private const val GETTER_NAME = "get"
private const val CACHE_FIELD_NAME = "icon"
private const val CACHED_LABEL = "piko_settings_icon_cached"
private const val GET_DRAWABLE_ID_DESCRIPTOR = "$PIKO_SETTINGS_ICON_DESCRIPTOR->getDrawableId()I"
