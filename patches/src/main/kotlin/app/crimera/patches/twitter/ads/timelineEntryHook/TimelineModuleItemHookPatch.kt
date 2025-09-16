package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val timelineModuleItemHookFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem\$\$JsonObjectMapper;" && it.name == "parse"
        }
    }

val timelineModuleItemHookPatch =
    bytecodePatch(
        description = "Hooks timeline item",
    ) {
        execute {
            compatibleWith("com.twitter.android")

            val methods = timelineModuleItemHookFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

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
