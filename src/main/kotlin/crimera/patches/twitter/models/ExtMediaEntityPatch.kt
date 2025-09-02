package crimera.patches.twitter.models

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class ExtMediaEntityPatch :
    BytecodePatch(
        setOf(
            ExtMediaHighResVideoMethodFinder,
            ExtMediaHighResVideoFingerprint,
            ExtMediaGetImageMethodFinder,
            ExtMediaGetImageFingerprint,
        ),
    ) {
    override fun execute(context: BytecodeContext) {
        val highResVideoMethodFinderResult =
            ExtMediaHighResVideoMethodFinder.result ?: throw PatchException("Could not find ExtMediaHighResVideoMethodFinder fingerprint")

        highResVideoMethodFinderResult.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str == "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment") {
                val inst =
                    highResVideoMethodFinderResult.mutableMethod.getInstructions().first {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            it.location.index > match.index
                    }
                val methodName = highResVideoMethodFinderResult.getMethodName(inst.location.index)
                ExtMediaHighResVideoFingerprint.changeFirstString(methodName)
                return@forEach
            }
        }
        
// ------------
        val imageMethodResult =
            ExtMediaGetImageMethodFinder.result ?: throw PatchException("Could not find ExtMediaGetImageMethodFinder fingerprint")

        val imageFieldName =
            imageMethodResult.getFieldName(
                imageMethodResult.mutableMethod
                    .getInstructions()
                    .last {
                        it.opcode ==
                            Opcode.IGET_OBJECT
                    }.location.index,
            )
        ExtMediaGetImageFingerprint.changeFirstString(imageFieldName)
// ------------
    }
}
