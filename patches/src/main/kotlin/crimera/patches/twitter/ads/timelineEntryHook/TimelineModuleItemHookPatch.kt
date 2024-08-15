package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR

internal val timelineModuleItemHookFingerprint = fingerprint {
    returns("Ljava/lang/Object")
    custom { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem\$\$JsonObjectMapper;" && it.name == "parse"
    }
}

@Suppress("unused")
val timelineModuleItemHookPatch = bytecodePatch {
    compatibleWith("com.twitter.android")

    val result by timelineModuleItemHookFingerprint()

    execute {
        val TIMELINE_ENTRY_DESCRIPTOR = "${PATCHES_DESCRIPTOR}/TimelineEntry"

        val methods = result.mutableMethod
        val instructions = methods.instructions

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(
            returnObj, """
        invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->checkEntry(Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;)Lcom/twitter/model/json/timeline/urt/JsonTimelineModuleItem;
        move-result-object p1
        """.trimIndent()
        )
    }
}