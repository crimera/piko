package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch

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

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(returnObj,"""
        invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;)Lcom/twitter/model/json/timeline/urt/JsonTimelineEntry;
        move-result-object p1
        """.trimIndent())

        //end
    }
}