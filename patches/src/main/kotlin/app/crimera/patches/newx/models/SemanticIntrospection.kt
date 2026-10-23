package app.crimera.patches.newx.models

import app.crimera.bytecode.Block
import app.crimera.patches.common.semantic.ModelFieldAccessor as SharedModelFieldAccessor
import app.crimera.patches.common.semantic.fieldForBooleanToStringLabel as sharedFieldForBooleanToStringLabel
import app.crimera.patches.common.semantic.fieldForToStringLabel as sharedFieldForToStringLabel
import app.crimera.patches.common.semantic.patchBooleanAccessorGetter as sharedPatchBooleanAccessorGetter
import app.crimera.patches.common.semantic.patchBooleanFieldGetter as sharedPatchBooleanFieldGetter
import app.crimera.patches.common.semantic.patchBooleanMethodGetter as sharedPatchBooleanMethodGetter
import app.crimera.patches.common.semantic.patchBridge as sharedPatchBridge
import app.crimera.patches.common.semantic.patchObjectAccessorGetter as sharedPatchObjectAccessorGetter
import app.crimera.patches.common.semantic.patchObjectFieldGetter as sharedPatchObjectFieldGetter
import app.crimera.patches.common.semantic.patchObjectMethodGetter as sharedPatchObjectMethodGetter
import app.crimera.patches.common.semantic.patchWideAccessorGetter as sharedPatchWideAccessorGetter
import app.crimera.patches.common.semantic.patchWideFieldGetter as sharedPatchWideFieldGetter
import app.crimera.patches.common.semantic.patchWideMethodGetter as sharedPatchWideMethodGetter
import app.crimera.patches.common.semantic.readModelAccessor as sharedReadModelAccessor
import app.crimera.patches.common.semantic.requireBridge as sharedRequireBridge
import app.crimera.patches.common.semantic.requireGetter as sharedRequireGetter
import app.crimera.patches.common.semantic.requirePublicFields as sharedRequirePublicFields
import app.crimera.patches.common.semantic.requireSingle as sharedRequireSingle
import app.crimera.patches.common.semantic.requireSingleInstanceField as sharedRequireSingleInstanceField
import app.crimera.patches.common.semantic.resolveCurrentMethod as sharedResolveCurrentMethod
import app.crimera.patches.common.semantic.resolveFieldAccessor as sharedResolveFieldAccessor
import app.crimera.patches.common.semantic.resolveMutableMethodOwner as sharedResolveMutableMethodOwner
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

internal typealias ModelFieldAccessor = SharedModelFieldAccessor

context(_: BytecodePatchContext)
internal fun Fingerprint.requireSingle(target: String): Match =
    this.sharedRequireSingle("NewX $target")

private inline fun <T> withNewXDiagnostic(block: () -> T): T {
    try {
        return block()
    } catch (exception: PatchException) {
        val detail = exception.message?.takeIf { it.isNotBlank() } ?: "semantic resolution failed"
        if (detail.startsWith("NewX ")) throw exception
        throw PatchException("NewX $detail", exception)
    }
}

internal fun Match.fieldForToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForToStringLabel(label) }

internal fun Method.fieldForToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForToStringLabel(label) }

internal fun Match.fieldForBooleanToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForBooleanToStringLabel(label) }

internal fun ClassDef.requireSingleInstanceField(
    type: String,
    semanticName: String,
): FieldReference =
    withNewXDiagnostic { this.sharedRequireSingleInstanceField(type, semanticName) }

internal fun MutableClass.requirePublicFields(fields: List<FieldReference>) =
    withNewXDiagnostic { this.sharedRequirePublicFields(fields) }

internal fun MutableClass.resolveFieldAccessor(
    field: FieldReference,
    semanticName: String,
): ModelFieldAccessor =
    withNewXDiagnostic { this.sharedResolveFieldAccessor(field, semanticName) }

internal fun MutableClass.requireGetter(
    field: FieldReference,
    semanticName: String,
): MethodReference =
    withNewXDiagnostic { this.sharedRequireGetter(field, semanticName) }

internal fun Block.readModelAccessor(
    accessor: ModelFieldAccessor,
    receiver: Int,
    destination: Int = receiver,
) = withNewXDiagnostic {
    this.sharedReadModelAccessor(accessor, receiver, destination)
}

internal fun MutableClass.patchObjectFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectFieldGetter(name, ownerDescriptor, field, returnType)
}

internal fun MutableClass.patchObjectMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectMethodGetter(name, ownerDescriptor, getter, returnType)
}

internal fun MutableClass.patchObjectAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectAccessorGetter(name, ownerDescriptor, accessor, returnType)
}

internal fun MutableClass.patchBooleanFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = withNewXDiagnostic {
    this.sharedPatchBooleanFieldGetter(name, ownerDescriptor, field)
}

internal fun MutableClass.patchBooleanMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = withNewXDiagnostic {
    this.sharedPatchBooleanMethodGetter(name, ownerDescriptor, getter)
}

internal fun MutableClass.patchBooleanAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = withNewXDiagnostic {
    this.sharedPatchBooleanAccessorGetter(name, ownerDescriptor, accessor)
}

internal fun MutableClass.patchWideFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = withNewXDiagnostic {
    this.sharedPatchWideFieldGetter(name, ownerDescriptor, field)
}

internal fun MutableClass.patchWideMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = withNewXDiagnostic {
    this.sharedPatchWideMethodGetter(name, ownerDescriptor, getter)
}

internal fun MutableClass.patchWideAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = withNewXDiagnostic {
    this.sharedPatchWideAccessorGetter(name, ownerDescriptor, accessor)
}

internal fun MutableClass.requireBridge(
    name: String,
    parameters: String,
    returnType: String,
): MutableMethod =
    withNewXDiagnostic { this.sharedRequireBridge(name, parameters, returnType) }

internal fun MutableMethod.patchBridge(block: Block.() -> Unit) =
    withNewXDiagnostic { this.sharedPatchBridge(block) }

context(_: BytecodePatchContext)
internal fun MethodReference.resolveCurrentMethod(label: String): Method =
    withNewXDiagnostic { this.sharedResolveCurrentMethod(label) }

context(_: BytecodePatchContext)
internal fun MethodReference.resolveMutableMethodOwner(label: String): Pair<MutableClass, MutableMethod> =
    withNewXDiagnostic { this.sharedResolveMutableMethodOwner(label) }
