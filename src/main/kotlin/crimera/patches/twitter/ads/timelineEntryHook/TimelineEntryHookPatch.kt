package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import app.revanced.patcher.fingerprint.MethodFingerprint

object TimelineEntryHookFingerprint:MethodFingerprint(
    returnType = "Ljava/lang/Object",
    customFingerprint = {it,_->
        it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry\$\$JsonObjectMapper;" && it.name == "parse"
    }
)

@Patch(
    name = "Hook for timeline entry",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object TimelineEntryHookPatch:BytecodePatch(
    setOf(TimelineEntryHookFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val TIMELINE_ENTRY_DESCRIPTOR = "${SettingsPatch.PATCHES_DESCRIPTOR}/TimelineEntry"

        val result = TimelineEntryHookFingerprint.result
            ?:throw PatchException("TimelineEntryHookFingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }

        methods.addInstructionsWithLabels(returnObj.location.index,"""
        invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->checkEntry(Ljava/lang/Object;)Z
        move-result v0
        if-eqz v0, :end
        const p1,0x0
        """.trimIndent(),
            ExternalLabel("end",returnObj))

        //end
    }
}