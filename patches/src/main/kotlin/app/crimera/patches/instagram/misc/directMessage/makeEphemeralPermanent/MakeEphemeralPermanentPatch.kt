/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.makeEphemeralPermanent

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.Opcode

internal object EphemeralMediaJsonParserFingerprint : Fingerprint(
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
    returnType = "Ljava/lang/Object;",
    strings = listOf("url_expire_at_secs", "view_mode", "seen_count", "tap_models"),
)

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description = "Changes unexpired view once, view twice media to permanent view.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity, saveAllMessagesPatch, directItemEntity, userDataEntity)
        execute {

            suppressEphemeralMediaReceipts()

            EphemeralMediaJsonParserFingerprint.apply {
                val expireAtStringIndex = stringMatches[0].index
                val viewModeStringIndex = stringMatches[1].index
                method.apply {
                    val viewModeIPutObjectInstruction =
                        getInstruction(
                            indexOfFirstInstruction(viewModeStringIndex, Opcode.IPUT_OBJECT),
                        )

                    val viewModeInstructionExtraction = viewModeIPutObjectInstruction.fieldExtractor()
                    val ephemeralMediaClassName = extensionToClassName(viewModeInstructionExtraction.definingClass)
                    val viewModeFieldName = viewModeInstructionExtraction.name

                    // The field stored right after the expire key, since other keys can now sit before view_mode.
                    val expireAtInstructionExtraction =
                        getInstruction(
                            indexOfFirstInstruction(expireAtStringIndex, Opcode.IPUT_OBJECT),
                        ).fieldExtractor()
                    val expireAtFieldName = expireAtInstructionExtraction.name

                    val returnObjectInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                    val ephemeralMediaClassRegister = returnObjectInstruction.registersUsed[0]

                    val midIfEqInstruction = instructions.filter { it.opcode == Opcode.IF_EQ }[1]
                    val lastIfEqIndex = midIfEqInstruction.location.index
                    val registers = midIfEqInstruction.registersUsed
                    val registerA = registers[0]
                    val registerB = registers[1]

                    addInstructionsWithLabels(
                        lastIfEqIndex,
                        """
                        if-ne v$registerA, v$registerB, :piko
                        
                        iget-object v0, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$expireAtFieldName:Ljava/lang/Long;
                        iget-object v1, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        
                        invoke-static {v0, v1}, $PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;->makeEphemeralMediaPermanent(Ljava/lang/Long;Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1                        
                        
                        iput-object v1, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        return-object v$ephemeralMediaClassRegister
                        """.trimIndent(),
                        ExternalLabel("piko", midIfEqInstruction),
                    )
                }
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }

private const val USER_SESSION = "Lcom/instagram/common/session/UserSession;"
private const val USER_MODEL = "Lcom/instagram/user/model/User;"
private const val EXTENSION = "$PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;"

private object EphemeralMediaSeenReceiptFingerprint : Fingerprint(
    filters = listOf(string("direct_v2/visual_threads/%s/item_seen/"), string("raven_media")),
)

context(patchContext: BytecodePatchContext)
private fun suppressEphemeralMediaReceipts() {
    val eventType = EphemeralMediaSeenReceiptFingerprint.method.instructions.firstOrNull {
        it.opcode == Opcode.CHECK_CAST
    }?.getReference<TypeReference>()?.type
        ?: throw PatchException("Missing ephemeral receipt event type")
    val producer = Fingerprint(
        definingClass = "Linstagram/features/direct/visual/internal/DirectVisualMessageViewerController;",
        returnType = "V",
        filters = listOf(string("directVisualViewerSummaryLogger")),
        custom = { method, _ ->
            method.implementation?.instructions?.any {
                it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == eventType
            } == true
        },
    ).method
    val creation = producer.instructions.withIndex().singleOrNull {
        it.value.opcode == Opcode.NEW_INSTANCE && it.value.getReference<TypeReference>()?.type == eventType
    } ?: throw PatchException("Expected one ephemeral receipt creation")
    val eventRegister = creation.value.registersUsed.single()
    val constructor = producer.getInstruction(creation.index + 1)
    val index = creation.index + 2
    val firstField = producer.getInstruction(index)
    if (constructor.opcode != Opcode.INVOKE_DIRECT ||
        constructor.getReference<MethodReference>()?.name != "<init>" ||
        constructor.registersUsed.firstOrNull() != eventRegister ||
        firstField.opcode != Opcode.IPUT_OBJECT ||
        firstField.getReference<FieldReference>()?.definingClass != eventType ||
        firstField.registersUsed.getOrNull(1) != eventRegister
    ) {
        throw PatchException("Unexpected ephemeral receipt initialization")
    }
    val unseenCall = producer.instructions.take(creation.index).singleOrNull {
        val reference = it.getReference<MethodReference>()
        it.opcode == Opcode.INVOKE_VIRTUAL && reference?.returnType == "Z" &&
            reference.parameterTypes == listOf(USER_MODEL)
    } ?: throw PatchException("Expected one ephemeral unread predicate")
    val unseenReference = unseenCall.getReference<MethodReference>()!!
    val itemRegister = unseenCall.registersUsed.first()
    val sessionCall = producer.instructions.take(creation.index).lastOrNull {
        it.opcode == Opcode.INVOKE_STATIC &&
            it.getReference<MethodReference>()?.parameterTypes?.firstOrNull() == USER_SESSION
    } ?: throw PatchException("Missing ephemeral receipt session")
    val sessionRegister = sessionCall.registersUsed.first()
    if (itemRegister > 15 || sessionRegister > 15) throw PatchException("Receipt arguments require range invoke")

    val unseen = Fingerprint(
        definingClass = unseenReference.definingClass,
        name = unseenReference.name,
        returnType = "Z",
        custom = { method, _ -> method.parameterTypes == listOf(USER_MODEL) },
    ).method
    val parser = EphemeralMediaJsonParserFingerprint.method
    val seenKey = parser.instructions.withIndex().single {
        it.value.getReference<StringReference>()?.string == "seen_count"
    }.index
    val seenField = parser.getInstruction(parser.indexOfFirstInstruction(seenKey, Opcode.IPUT))
        .getReference<FieldReference>() ?: throw PatchException("Missing ephemeral seen count")
    if (seenField.type != "I" || unseen.instructions.none { it.getReference<FieldReference>() == seenField }) {
        throw PatchException("Unread predicate does not inspect the ephemeral seen count")
    }
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
    ).getFreeRegister()
    // Stop before enqueueing: returning from the network handler would leave its queued task unfinished.
    producer.addInstructionsWithLabels(
        index,
        """
        invoke-static {v$sessionRegister, v$itemRegister}, $EXTENSION->shouldSuppressEphemeralMediaReceipt(${USER_SESSION}Ljava/lang/Object;)Z
        move-result v$register
        if-eqz v$register, :piko_send_receipt
        return-void
        """.trimIndent(),
        ExternalLabel("piko_send_receipt", firstField),
    )
}
