/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.makeEphemeralPermanent

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.patches.instagram.utils.typeReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "$PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;"

internal object EphemeralMediaJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        // The key switch of the parser loop.
        opcode(Opcode.IF_EQ),
        methodCall(returnType = "Ljava/lang/String;", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(definingClass = "Ljava/lang/String;", name = "hashCode", location = MatchAfterImmediately()),
        string("url_expire_at_secs"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/lang/Long;"),
        string("tap_models"),
        string("view_mode"),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = "Ljava/lang/String;"),
        string("seen_count"),
        opcode(Opcode.RETURN_OBJECT),
    ),
)

// The binder of photo and video messages, found by the bubble it inflates.
private object VisualMessageBinderFingerprint : Fingerprint(
    filters = listOf(resourceLiteral(ResourceType.ID, "message_content_visual_thumbnail_bubble_container")),
)

// Fills the views of a photo or video message with the message; it is the binder's only method with two object parameters.
private object VisualMessageBindFingerprint : Fingerprint(
    classFingerprint = VisualMessageBinderFingerprint,
    returnType = "V",
    parameters = listOf("L", "L"),
    custom = { method, _ -> !AccessFlags.STATIC.isSet(method.accessFlags) },
)

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description = "Changes unexpired view once, view twice media to permanent view.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity, saveAllMessagesPatch, directItemEntity, userDataEntity)
        execute {

            suppressEphemeralMediaReceipts()

            EphemeralMediaJsonParserFingerprint.let {
                markServerResponseParsers(it.method.parameterTypes.single().toString())
                if (it.method.implementation!!.registerCount > 16) {
                    throw PatchException("Ephemeral parser argument requires a range invoke")
                }
                val ifEq = it.instructionMatches.first()
                val expireAtField = it.instructionMatches[5].fieldReference
                val viewModeField = it.instructionMatches[8].fieldReference
                val ephemeralMediaRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
                val (registerA, registerB) = ifEq.instruction.registersUsed

                it.method.addInstructionsWithLabels(
                    ifEq.index,
                    """
                    if-ne v$registerA, v$registerB, :piko

                    iget-object v0, v$ephemeralMediaRegister, $expireAtField
                    iget-object v1, v$ephemeralMediaRegister, $viewModeField

                    invoke-static {v0, v1, p1}, $EXTENSION->makeEphemeralMediaPermanent(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v1

                    iput-object v1, v$ephemeralMediaRegister, $viewModeField
                    return-object v$ephemeralMediaRegister
                    """.trimIndent(),
                    ExternalLabel("piko", it.method.getInstruction(ifEq.index)),
                )
            }
            // The parameters sit above v15, so they are copied to v0 and v1 (unused at the start) to be passed on.
            VisualMessageBindFingerprint.method.addInstructions(
                0,
                """
                move-object/from16 v0, p1
                move-object/from16 v1, p2
                invoke-static {v0, v1}, $PATCHES_DESCRIPTOR/dm/EphemeralMediaCaption;->onBind(Ljava/lang/Object;Ljava/lang/Object;)V
                """,
            )

            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }

context(_: BytecodePatchContext)
private fun markServerResponseParsers(parserType: String) {
    val match = Fingerprint(
        filters = listOf(
            string("AutoResponseParserUtil.wrapStream"),
            methodCall(parameters = listOf("Ljava/io/InputStream;"), returnType = parserType),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        ),
    ).matchSingle()
    val result = match.instructionMatches.last()
    val register = result.instruction.registersUsed.single()
    // Mark the HTTP parser itself; local message deserializers create different instances.
    match.method.addInstructions(
        result.index + 1,
        "invoke-static/range {v$register .. v$register}, $PATCHES_DESCRIPTOR/dm/EphemeralMediaCaption;->markServerParser(Ljava/lang/Object;)V",
    )
}

private object EphemeralMediaSeenReceiptFingerprint : Fingerprint(
    filters = listOf(
        // The receipt event type.
        opcode(Opcode.CHECK_CAST),
        string("direct_v2/visual_threads/%s/item_seen/"),
        string("raven_media"),
    ),
)

private fun ephemeralMediaReceiptProducerFingerprint(eventType: String) = Fingerprint(
    definingClass = "Linstagram/features/direct/visual/internal/DirectVisualMessageViewerController;",
    returnType = "V",
    filters = listOf(
        string("directVisualViewerSummaryLogger"),
        // Unread predicate of the viewed item.
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf(USER_MODEL_CLASS_NAME), returnType = "Z"),
        newInstance(eventType),
        methodCall(name = "<init>", location = MatchAfterImmediately()),
    ),
)

context(patchContext: BytecodePatchContext)
private fun suppressEphemeralMediaReceipts() {
    val eventType = EphemeralMediaSeenReceiptFingerprint.instructionMatches.first().typeReference
    val producerMatch = ephemeralMediaReceiptProducerFingerprint(eventType).matchSingle()
    val producer = producerMatch.method
    val unseenCall = producerMatch.instructionMatches[1]
    val unseenReference = unseenCall.methodReference
    val creationIndex = producerMatch.instructionMatches[2].index
    // The last item getter replaces its receiver with the client-context String on 451.
    // Suppress the receipt while the receiver still contains the DirectItem.
    val index = producer.indexOfFirstInstructionReversedOrThrow(creationIndex) {
        opcode == Opcode.INVOKE_VIRTUAL && getReference<MethodReference>()?.let {
            it.definingClass == unseenReference.definingClass &&
                it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
        } == true
    }
    val itemRegister = producer.getInstruction(index).registersUsed.single()
    val sessionRegister = producer.getInstruction(
        producer.indexOfFirstInstructionReversedOrThrow(index) {
            opcode == Opcode.INVOKE_STATIC &&
                getReference<MethodReference>()?.parameterTypes?.firstOrNull()?.toString() == USER_SESSION_CLASS
        },
    ).registersUsed.first()
    if (itemRegister > 15 || sessionRegister > 15) throw PatchException("Receipt arguments require range invoke")

    val unseen = Fingerprint(
        definingClass = unseenReference.definingClass,
        name = unseenReference.name,
        returnType = "Z",
        parameters = listOf(USER_MODEL_CLASS_NAME),
    ).method
    val unreadRegister = unseen.getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
    unseen.addInstructionsWithLabels(
        0,
        """
        invoke-static {p0, p1}, $EXTENSION->wasEphemeralMediaViewed(Ljava/lang/Object;Ljava/lang/Object;)Z
        move-result v$unreadRegister
        if-eqz v$unreadRegister, :piko_native_unread
        const/4 v$unreadRegister, 0x0
        return v$unreadRegister
        """.trimIndent(),
        ExternalLabel("piko_native_unread", unseen.getInstruction(0)),
    )

    val register = producer.getFreeRegisterProvider(
        index = index,
        numberOfFreeRegistersNeeded = 1,
        *intArrayOf(itemRegister, sessionRegister),
    ).getFreeRegister()
    // Stop before enqueueing: returning from the network handler would leave its queued task unfinished.
    producer.addInstructionsWithLabels(
        index,
        """
        invoke-static {v$sessionRegister, v$itemRegister}, $EXTENSION->shouldSuppressEphemeralMediaReceipt(${USER_SESSION_CLASS}Ljava/lang/Object;)Z
        move-result v$register
        if-eqz v$register, :piko_send_receipt
        return-void
        """.trimIndent(),
        ExternalLabel("piko_send_receipt", producer.getInstruction(index)),
    )
}
