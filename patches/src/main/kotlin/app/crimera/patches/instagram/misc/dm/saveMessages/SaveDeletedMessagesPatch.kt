/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private const val HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/dm/SavedMessagesHook;"

@Suppress("unused")
val saveDeletedMessagesPatch =
    bytecodePatch(
        name = "Save deleted messages",
        description = "Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them.",
    ) {
        dependsOn(settingsPatch, dmActionBarButtonPatch)
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
            DirectItemPostprocessFingerprint.method.apply {
                // A0P is the MQTT post-processing step called for every incoming DirectItem.
                // Inject at index 0: `this` (p0) is fully populated before A0P runs.
                // onMessageReceived offloads work to a background HandlerThread so the
                // MQTT delivery thread is never blocked (critical on MediaTek/mt6855).
                val reg = getFreeRegisterProvider(index = 0, numberOfFreeRegistersNeeded = 1).getFreeRegister()
                addInstructions(
                    0,
                    """
                    move-object/from16 v$reg, p0
                    invoke-static {v$reg}, $HOOK_CLASS->onMessageReceived(Ljava/lang/Object;)V
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

            enableSettings("saveDeletedMessages")
        }
    }
