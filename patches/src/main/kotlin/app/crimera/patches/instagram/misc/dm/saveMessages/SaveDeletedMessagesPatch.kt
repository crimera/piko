/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/dm/SavedMessagesHook;"
private const val DIRECT_THREAD_KEY = "Lcom/instagram/model/direct/DirectThreadKey;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
    ) {
        dependsOn(settingsPatch, dmActionBarButtonPatch, directItemEntity, userDataEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // --- Hook 1: Capture each message as it arrives from server ---
            // DirectItemFieldParserFingerprint matches LX/0gL;.A00 (the per-field JSON
            // dispatch helper) to locate the parser class. We then hook parseFromJson in
            // that same class — it returns the fully-populated DirectItem (LX/9ZA;).
            // This is necessary because parseFromJson itself contains no string constants
            // (it just delegates to unsafeParseFromJson), while A00 uniquely identifies
            // the class via "item_id" + "hide_in_thread" only present together in classes12.
            DirectItemFieldParserFingerprint.classDef.methods.first { it.name == "parseFromJson" }.apply {
                val returnObjInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                val returnObjIndex = returnObjInstruction.location.index
                val itemRegister = returnObjInstruction.registersUsed[0]

                addInstructions(
                    returnObjIndex,
                    """
                    invoke-static {v$itemRegister}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // --- Hook 2: Capture messages delivered via MQTT/MSys real-time sync ---
            // parseFromJson (Hook 1) only fires for REST/JSON loads (thread history).
            // Real-time messages arrive via MQTT Thrift encoding and never touch parseFromJson.
            // A0P is the post-processing step called after every MQTT DirectItem is assembled.
            // Confirmed live on mt6855: A0P fires for new incoming DMs.
            // We hook every RETURN_OBJECT that returns a non-null value (p0/this) so that
            // obfuscation-driven instruction reordering doesn't shift which is "last".
            // Resolve, at patch time, the MSys-delta field that holds the thread id. A0P's second
            // parameter is the MSys delta (LX/02L). A stable converter
            // (LX/3Bh;->A00(delta) : DirectThreadKey, invoked inside the action-bar builder) reads
            // that thread id straight off the delta — its first iget-object of a String off the
            // delta is the field we want. DirectThreadKey is a non-obfuscated class, so the converter
            // is found by return type, and the delta field is read out of its body — no hardcoded
            // obfuscated names, no runtime graph walk.
            val converterRef =
                DMActionBarThreadFingerprint.method.instructions
                    .first {
                        it.opcode == Opcode.INVOKE_STATIC &&
                            (it as ReferenceInstruction).reference
                                .let { r -> r is MethodReference && r.returnType == DIRECT_THREAD_KEY }
                    }.let { (it as ReferenceInstruction).reference as MethodReference }
            val deltaThreadIdField =
                mutableClassDefBy { it.type == converterRef.definingClass }
                    .methods
                    .first { it.name == converterRef.name && it.returnType == DIRECT_THREAD_KEY }
                    .instructions
                    .first {
                        it.opcode == Opcode.IGET_OBJECT &&
                            (it as ReferenceInstruction).reference
                                .let { r -> r is FieldReference && r.type == "Ljava/lang/String;" }
                    }.let { (it as ReferenceInstruction).reference as FieldReference }
            val deltaClass = deltaThreadIdField.definingClass
            // Sanity-check the delta really is A0P's second parameter before reading it off p2.
            require(DirectItemPostprocessFingerprint.method.parameterTypes[1] == deltaClass) {
                "A0P param[1] (${DirectItemPostprocessFingerprint.method.parameterTypes[1]}) != delta $deltaClass"
            }

            DirectItemPostprocessFingerprint.method.apply {
                // A0P is the MQTT post-processing step called for every incoming DirectItem.
                // Inject at index 0: `this` (p0) is fully populated before A0P runs; p2 is the MSys
                // delta carrying the thread id (read via the patch-resolved field above).
                // onMessageReceived offloads work to a background HandlerThread so the
                // MQTT delivery thread is never blocked (critical on MediaTek/mt6855).
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 3)
                val rItem = regs.getFreeRegister()
                val rDelta = regs.getFreeRegister()
                val rTid = regs.getFreeRegister()
                // p2 (the MSys delta) is null on some A0P calls — guard before reading the thread id,
                // and pass a null hint in that case (the extension then leaves the row unscoped).
                addInstructions(
                    0,
                    """
                    move-object/from16 v$rItem, p0
                    const/4 v$rTid, 0x0
                    move-object/from16 v$rDelta, p2
                    if-eqz v$rDelta, :piko_no_delta
                    iget-object v$rTid, v$rDelta, $deltaClass->${deltaThreadIdField.name}:Ljava/lang/String;
                    :piko_no_delta
                    invoke-static {v$rItem, v$rTid}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // --- Hook 4: Intercept the SQLite DAO "delete by item_id" call ---
            // When Instagram unsends a message it calls this DAO to remove it from the
            // local SQLite DB.  The item_id arrives as p2 (server_item_id) / p3 (client_item_id)
            // directly — no reflection or obfuscated field names needed.
            // We inject at entry (index 0) so the record is still in Instagram's DB and in
            // our vault at the time the hook fires.
            //   v408: LX/0LJ;.A0P(DirectThreadKey, String, String)V
            //   v4xx: (class name changes, string anchor stays the same)
            DirectItemDbHideFingerprint.method.apply {
                val regs = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 3)
                val r0 = regs.getFreeRegister()   // p0 = DAO object (LX/0LJ;) — used to get SQLiteDatabase
                val r1 = regs.getFreeRegister()   // p2 = server_item_id
                val r2 = regs.getFreeRegister()   // p3 = client_item_id

                addInstructions(
                    0,
                    """
                    move-object/from16 v$r0, p0
                    move-object/from16 v$r1, p2
                    move-object/from16 v$r2, p3
                    invoke-static {v$r0, v$r1, v$r2}, $HOOK_CLASS->onMessageHiddenFromDb(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }

            // --- Hook 5: record the open chat's action-bar controller ---
            // The DM action-bar builder A01(LX/2p9;)V receives the per-thread controller as p0.
            // The controller's only DirectThreadKey is built on-demand inside a deeply-gated Bloks
            // feature branch that never runs on a normal chat open, so anchoring on that key (the
            // old approach) meant noteOpenThreadId never fired. Instead we hand the controller object
            // (always present as p0) to the extension at method entry; the extension recovers the
            // open thread id from its object graph (the same bounded search used for message items),
            // so the per-chat screen can scope itself.
            DMActionBarThreadFingerprint.method.apply {
                val reg = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
                addInstructions(
                    0,
                    """
                    move-object/from16 v$reg, p0
                    invoke-static {v$reg}, $HOOK_CLASS->noteOpenThreadController(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // --- Hook 6: harvest participant usernames at thread/inbox load (patch-time source) ---
            // The DM thread deserializer LX/6o9;.A00 parses the "users" JSON key into a
            // List<com.instagram.user.model.User> and stores it on the thread object. We find that
            // store and hand the list to the extension, which reads each user's id + @handle via the
            // patch-resolved UserData accessors and fills the username directory. This runs as the
            // inbox/threads load (before any unsend), so notifications + the screen show real names.
            ThreadUsersDispatchFingerprint.method.apply {
                val insns = instructions.toList()
                val usersKeyIndex =
                    insns.indexOfFirst {
                        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                            (it as ReferenceInstruction).reference.toString() == "users"
                    }
                require(usersKeyIndex >= 0) { "const-string 'users' not found in thread dispatch" }
                // First store of the parsed list after the "users" key: iput-object <list>, ..., :List
                val listPutInstruction =
                    insns.drop(usersKeyIndex + 1).first {
                        it.opcode == Opcode.IPUT_OBJECT &&
                            (it as ReferenceInstruction).reference.toString().endsWith(":Ljava/util/List;")
                    }
                val listRegister = listPutInstruction.registersUsed[0]
                val putIndex = listPutInstruction.location.index
                val free = getFreeRegisterProvider(putIndex + 1, 1).getFreeRegister()
                addInstructions(
                    putIndex + 1,
                    """
                    move-object/from16 v$free, v$listRegister
                    invoke-static {v$free}, $HOOK_CLASS->noteThreadUsers(Ljava/util/List;)V
                    """.trimIndent(),
                )
            }

            enableSettings("saveDeletedMessages")
        }
    }
