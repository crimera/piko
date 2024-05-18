package crimera.patches.instagram.interaction.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.iface.reference.Reference
import crimera.patches.instagram.interaction.download.fingerprints.hook.*


object DownloadPatchHooksFingerprint : MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
        methodDef.definingClass == "Lapp/revanced/integrations/instagram/patches/download/DownloadPatch;"
                && methodDef.name == "feedItemClassName"
    }
)

object SetupHookSignaturesPatch : BytecodePatch(
    setOf(
        DownloadPatchHooksFingerprint,
        FeedItemClassFingerprint,
        FeedItemIconsFingerprint,
        MediaListFingerprint
    )
) {

    override fun execute(context: BytecodeContext) {
        DownloadPatchHooksFingerprint.result?.mutableClass?.let { classDef ->
            val methods = classDef.methods

            methods.getMethod("feedItemClassName")?.let {
                val feedItemClassName = FeedItemClassFingerprint.result?.classDef
                    ?: throw FeedItemClassFingerprint.exception

                it.setReturnString(feedItemClassName.toString())
            }

            methods.getMethod("feedOptionItemIconClassName")?.let {
                val feedItemIconsClassName = FeedItemIconsFingerprint.result?.classDef
                    ?: throw FeedItemIconsFingerprint.exception

                it.setReturnString(feedItemIconsClassName.toString())
            }

            methods.getMethod("mediaListSourceClass")?.let {
                val mediaListClassName = MediaListFingerprint.result?.classDef
                    ?: throw MediaListFingerprint.exception

                it.setReturnString(mediaListClassName.toString())
            }

            // set media list field
            methods.getMethod("listFieldName")?.let {
                val method = MediaListFingerprint.result?.mutableMethod
                    ?: throw MediaListFingerprint.exception

                val loc = method.getInstructions().filter { it.opcode == Opcode.CONST_STRING }
                    .firstOrNull { builderInstruction ->
                        (builderInstruction as BuilderInstruction21c).reference.toString() == "caption_is_edited"
                    }?.location?.index?.plus(2)

                if (loc != null) {
                    (method.getInstruction(loc) as BuilderInstruction22c).reference.getField()?.also { fieldName ->
                        it.setReturnString(fieldName)
                    }
                }

            }
        }
    }

    private fun Reference.getField(): String? {
        return """->(\w+):""".toRegex()
            .find(this.toString())?.groupValues?.get(
                1
            )
    }

    private fun MutableMethod.setReturnString(className: String) {
        this.replaceInstruction(0, "const-string v0, \"${className.toClassName()}\"")
    }

    private fun Set<MutableMethod>.getMethod(name: String): MutableMethod? {
        return this.firstOrNull { it.name == name }
    }

    private fun String.toClassName() =
        this.replace("/".toRegex(), ".").removePrefix("L").removeSuffix(";")
}
