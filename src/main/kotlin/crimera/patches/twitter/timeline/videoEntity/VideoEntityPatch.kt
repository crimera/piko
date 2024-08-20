package crimera.patches.twitter.timeline.videoEntity

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch

object VideoEntityFinderFingerprint: MethodFingerprint(
    strings = listOf(
        "video_configurations_amplify_video_bird_url_android_enabled",
    ),
    returnType = "Ljava/lang/String"
)

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
@Suppress("unused")
object VideoEntityPatch:BytecodePatch(
    setOf(VideoEntityFinderFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = VideoEntityFinderFingerprint.result
            ?:throw PatchException("VideoEntityFinderFingerprint not found")

        val m1 = result.mutableMethod
        val i1 = m1.getInstructions()

        val f1 = i1.last { it.opcode == Opcode.INVOKE_STATIC }.location.index
        val fr = m1.getInstruction<ReferenceInstruction>(f1).reference as DexBackedMethodReference
        val clsName = fr.definingClass
        val mName = fr.name

        val cls = context.findClass(clsName)!!.mutableClass
        val m2 = cls.methods.find { it.name == mName }
        val i2 = m2!!.getInstructions()

        val iget = i2.find { it.opcode == Opcode.IGET_OBJECT }!!.location.index
        m2.addInstructions(iget+1,"""
            invoke-static {p1}, ${SettingsPatch.PATCHES_DESCRIPTOR}/TimelineVideoEntity;->videoEnity(Ljava/util/List;)Ljava/util/List;
            move-result-object p1
        """.trimIndent())


    }
}