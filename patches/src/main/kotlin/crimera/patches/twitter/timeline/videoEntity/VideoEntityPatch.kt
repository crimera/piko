package crimera.patches.twitter.timeline.videoEntity

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.settingsPatch

val videoEntityFinderFingerprint =
    fingerprint {
        strings("video_configurations_amplify_video_bird_url_android_enabled")
        returns("Ljava/lang/String;")
    }

@Suppress("unused")
val videoEntityPatch =
    bytecodePatch {
        dependsOn(settingsPatch)
        compatibleWith("com.twitter.android")
        execute { context ->
            val result by videoEntityFinderFingerprint()

            val m1 = result.mutableMethod
            val i1 = m1.instructions

            val f1 = i1.last { it.opcode == Opcode.INVOKE_STATIC }.location.index
            val fr = m1.getInstruction<ReferenceInstruction>(f1).reference as DexBackedMethodReference
            val clsName = fr.definingClass
            val mName = fr.name

            val cls = context.classByType(clsName)!!.mutableClass
            val m2 = cls.methods.find { it.name == mName }
            val i2 = m2!!.instructions

            val iget = i2.find { it.opcode == Opcode.IGET_OBJECT }!!.location.index
            m2.addInstructions(
                iget + 1,
                """
                invoke-static {p1}, ${PATCHES_DESCRIPTOR}/TimelineVideoEntity;->videoEnity(Ljava/util/List;)Ljava/util/List;
                move-result-object p1
                """.trimIndent(),
            )
        }
    }
