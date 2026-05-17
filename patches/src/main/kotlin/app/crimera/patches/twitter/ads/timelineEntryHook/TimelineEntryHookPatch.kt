/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object TimelineEntryHookFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

private object TimelineModuleItemHookFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

val timelineEntryHookPatch =
    bytecodePatch(
        description = "Hooks timeline object",
    ) {
        execute {
            var methods = TimelineEntryHookFingerprint.method
            var instructions = methods.instructions

            var returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/TimelineEntry;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;)Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;
                move-result-object p1
                """.trimIndent(),
            )

            methods = TimelineModuleItemHookFingerprint.method
            instructions = methods.instructions

            returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/TimelineEntry;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;)Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;
                move-result-object p1
                """.trimIndent(),
            )
            // ends.
        }
    }
