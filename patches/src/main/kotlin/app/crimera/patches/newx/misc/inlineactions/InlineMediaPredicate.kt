package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.models.ResolvedNewXInlineMediaModels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.util.cloneMutable

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val ITERABLE_DESCRIPTOR = "Ljava/lang/Iterable;"
private const val ITERATOR_DESCRIPTOR = "Ljava/util/Iterator;"
private const val NEWX_UTILS_DESCRIPTOR = "Lapp/morphe/extension/newx/utils/NewXUtils;"
private const val INLINE_DOWNLOAD_BUTTON_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/InlineDownloadButton;"

private const val VIDEO_MODEL_LABEL = "piko_newx_inline_media_video"
private const val MEDIA_KIND_LABEL = "piko_newx_inline_media_kind"
private const val VARIANTS_READY_LABEL = "piko_newx_inline_media_variants_ready"
private const val VARIANT_LOOP_LABEL = "piko_newx_inline_media_variant_loop"
private const val VARIANT_MATCH_LABEL = "piko_newx_inline_media_variant_match"
private const val FALSE_LABEL = "piko_newx_inline_media_false"

/** Inject the composition-only predicate into its Object boundary stub. */
internal fun patchCompositionMediaPredicate(
    stub: MutableMethod,
    models: ResolvedNewXInlineMediaModels,
): MutableMethod {
    val registerCount = stub.implementation?.registerCount
        ?: throw PatchException("NewX composition media predicate has no implementation: $stub")
    val requiredRegisterCount = 14
    val helper = if (registerCount < requiredRegisterCount) {
        stub.cloneMutable(additionalRegisters = requiredRegisterCount - registerCount)
    } else {
        stub
    }
    if (helper.p0Register + helper.numberOfParameterRegisters > 16) {
        throw PatchException("NewX composition media predicate exceeds four-bit registers: $helper")
    }

    helper.insertHook(0, relocateBranchTargets = false) {
        val mediaRegister = helper.p0Register
        val checkRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val workRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val variantsRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val iteratorRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val variantRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val urlRegister = scratchRegister(RegisterLimit.FOUR_BIT)
        val contentTypeRegister = scratchRegister(RegisterLimit.FOUR_BIT)

        ifEqz(mediaRegister, Target.Local(FALSE_LABEL))
        move(workRegister, mediaRegister, OBJECT_DESCRIPTOR)
        instanceOf(checkRegister, mediaRegister, models.imageMediaDescriptor)
        ifEqz(checkRegister, Target.Local(MEDIA_KIND_LABEL))
        checkCast(workRegister, models.imageMediaDescriptor)
        iget(urlRegister, workRegister, models.imageUrlField)
        ifEqz(urlRegister, Target.Local(FALSE_LABEL))
        invokeStatic(
            methodReference(
                "$NEWX_UTILS_DESCRIPTOR->isHttpUrl($STRING_DESCRIPTOR)Z",
            ),
            urlRegister,
        )
        moveResult(checkRegister, "Z")
        returnValue(checkRegister)

        label(MEDIA_KIND_LABEL)
        instanceOf(checkRegister, mediaRegister, models.gifMediaDescriptor)
        ifEqz(checkRegister, Target.Local(VIDEO_MODEL_LABEL))
        checkCast(workRegister, models.gifMediaDescriptor)
        iget(variantsRegister, workRegister, models.gifVariantsField)
        goto(Target.Local(VARIANTS_READY_LABEL))

        label(VIDEO_MODEL_LABEL)
        instanceOf(checkRegister, mediaRegister, models.videoMediaDescriptor)
        ifEqz(checkRegister, Target.Local(FALSE_LABEL))
        checkCast(workRegister, models.videoMediaDescriptor)
        iget(variantsRegister, workRegister, models.videoVariantsField)

        label(VARIANTS_READY_LABEL)
        ifEqz(variantsRegister, Target.Local(FALSE_LABEL))
        checkCast(variantsRegister, ITERABLE_DESCRIPTOR)
        invokeInterface(
            methodReference("$ITERABLE_DESCRIPTOR->iterator()$ITERATOR_DESCRIPTOR"),
            variantsRegister,
        )
        moveResult(iteratorRegister, ITERATOR_DESCRIPTOR)

        label(VARIANT_LOOP_LABEL)
        invokeInterface(
            methodReference("$ITERATOR_DESCRIPTOR->hasNext()Z"),
            iteratorRegister,
        )
        moveResult(checkRegister, "Z")
        ifEqz(checkRegister, Target.Local(FALSE_LABEL))
        invokeInterface(
            methodReference("$ITERATOR_DESCRIPTOR->next()$OBJECT_DESCRIPTOR"),
            iteratorRegister,
        )
        moveResult(variantRegister, OBJECT_DESCRIPTOR)
        instanceOf(checkRegister, variantRegister, models.variantDescriptor)
        ifEqz(checkRegister, Target.Local(VARIANT_LOOP_LABEL))
        checkCast(variantRegister, models.variantDescriptor)
        iget(urlRegister, variantRegister, models.variantUrlField)
        iget(contentTypeRegister, variantRegister, models.variantContentTypeField)
        invokeStatic(
            methodReference(
                "$INLINE_DOWNLOAD_BUTTON_DESCRIPTOR->isHttpMp4Variant(" +
                    "$STRING_DESCRIPTOR$STRING_DESCRIPTOR)Z",
            ),
            urlRegister,
            contentTypeRegister,
        )
        moveResult(checkRegister, "Z")
        ifNez(checkRegister, Target.Local(VARIANT_MATCH_LABEL))
        goto(Target.Local(VARIANT_LOOP_LABEL))

        label(VARIANT_MATCH_LABEL)
        constInt(checkRegister, 1)
        returnValue(checkRegister)

        label(FALSE_LABEL)
        constInt(checkRegister, 0)
        returnValue(checkRegister)
    }
    return helper
}
