/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.common.declaredParameterRegister
import app.crimera.patches.common.parameterRegisterStart
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * Adds a static bridge to [owner], which adds a raw story media to the pending seen stories
 * using the typed media wrapper the native story consumption creates.
 */
internal fun addStoryAggregateBridge(
    owner: MutableClass,
    aggregate: MethodReference,
    rawMediaType: String,
    metadataConstructor: MethodReference,
    wrapperType: String,
    wrapperConstructor: MethodReference,
): MethodReference {
    val bridge = ImmutableMethod(
        owner.type,
        "pikoAddStorySeen",
        listOf(rawMediaType, USER_SESSION_CLASS, owner.type, STRING_CLASS).map {
            ImmutableMethodParameter(it, emptySet(), null)
        },
        "V",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
        emptySet(),
        emptySet(),
        MutableMethodImplementation(8),
    ).toMutable().apply {
        addInstructions(
            0,
            """
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v0
            const v1, -0x18b1a0fe
            const-string v2, "itas-android"
            new-instance v3, ${metadataConstructor.definingClass}
            invoke-direct {v3, v0, v1, v2}, $metadataConstructor
            new-instance v0, $wrapperType
            invoke-direct {v0, v3, p0}, $wrapperConstructor
            invoke-virtual {p2, p1, v0, p3}, $aggregate
            return-void
            """.trimIndent(),
        )
    }
    owner.methods.add(bridge)
    return bridge
}

/**
 * Root view field of a story view holder, which is stored by the constructor of the class or one of its superclasses.
 */
context(context: BytecodePatchContext)
internal fun storyRootViewField(classType: String): FieldReference =
    generateSequence(classType) { context.classDefBy(it).superclass }
        .firstNotNullOf { constructorRootViewFingerprint(it).matchOrNull() }
        .instructionMatches.first().fieldReference

internal fun addStoryProgressCaptureHook(
    method: MutableMethod,
    bridgeMethodName: String,
) {
    method.addInstruction(
        0,
        "invoke-static/range {p0 .. p0}, $STORY_SEEN_BRIDGE_DESCRIPTOR->$bridgeMethodName(Ljava/lang/Object;)V",
    )
}

internal fun addStoryProgressBridgeHook(
    method: MutableMethod,
    controllerClass: String,
    sessionField: FieldReference,
    itemField: FieldReference,
    reelField: FieldReference,
    reelModelField: FieldReference,
    reelTypeField: FieldReference,
    rootViewField: FieldReference,
) {
    method.addInstructions(
        0,
        """
        check-cast p0, $controllerClass
        iget-object p1, p0, $sessionField
        iget-object p2, p0, $itemField
        if-eqz p2, :piko_story_progress_unavailable
        iget-object p3, p0, $reelField
        if-eqz p3, :piko_story_progress_unavailable
        iget-object p4, p3, $reelModelField
        if-eqz p4, :piko_story_progress_unavailable
        iget-object p4, p4, $reelTypeField
        iget-object p0, p0, $rootViewField
        if-eqz p0, :piko_story_progress_unavailable
        invoke-static {p1, p2, p3, p4, p0}, $STORY_SEEN_BUTTON_DESCRIPTOR->captureCurrentStoryFromProgress(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        return-void
        :piko_story_progress_unavailable
        invoke-static {}, $STORY_SEEN_BUTTON_DESCRIPTOR->invalidateCurrentStory()V
        return-void
        """.trimIndent(),
    )
}

/**
 * Captures the bound story at the start and at every return of the static story header binder.
 */
internal fun addStoryHeaderCaptureHook(method: MutableMethod) {
    val captureInstructions =
        """
        move-object/from16 v0, p$STORY_HEADER_SESSION_PARAMETER
        move-object/from16 v1, p$STORY_HEADER_ITEM_PARAMETER
        move-object/from16 v2, p$STORY_HEADER_REEL_PARAMETER
        move-object/from16 v3, p$STORY_HEADER_HOLDER_PARAMETER
        invoke-static/range {v0 .. v3}, $STORY_SEEN_BRIDGE_DESCRIPTOR->captureFromHeaderBind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        """.trimIndent()
    method.addInstructions(0, captureInstructions)

    method.findInstructionIndicesReversedOrThrow(Opcode.RETURN_VOID).forEach { returnIndex ->
        method.addInstructions(returnIndex, captureInstructions)
    }
}

internal fun addStoryHeaderBridgeHook(
    method: MutableMethod,
    holderClass: String,
    rootViewField: FieldReference,
    reelModelField: FieldReference,
    reelTypeField: FieldReference,
) {
    method.addInstructions(
        0,
        """
        check-cast p2, ${reelModelField.definingClass}
        check-cast p3, $holderClass
        if-eqz p1, :piko_story_header_unavailable
        if-eqz p2, :piko_story_header_unavailable
        if-eqz p3, :piko_story_header_unavailable
        iget-object p4, p3, $rootViewField
        if-eqz p4, :piko_story_header_unavailable
        iget-object p5, p2, $reelModelField
        if-eqz p5, :piko_story_header_unavailable
        iget-object p5, p5, $reelTypeField
        move-object p3, p5
        invoke-static/range {p0 .. p4}, $STORY_SEEN_BUTTON_DESCRIPTOR->captureCurrentStoryFromHeader(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        return-void
        :piko_story_header_unavailable
        invoke-static {}, $STORY_SEEN_BUTTON_DESCRIPTOR->invalidateCurrentStory()V
        return-void
        """.trimIndent(),
    )
}
