package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.fingerprint.MethodFingerprintResult
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal abstract class NativeDownloaderMethodFingerprint(
    private val methodName: String,
) : MethodFingerprint(customFingerprint = { methodDef, classDef ->
        methodDef.name == methodName && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
    })

internal object TweetObjectFingerprint : MethodFingerprint(strings = listOf("https://x.com/%1\$s/status/%2\$d"))

internal object GetTweetClassFingerprint : NativeDownloaderMethodFingerprint("getTweetClass")

internal object GetTweetIdFingerprint : NativeDownloaderMethodFingerprint("getTweetId")

internal object GetTweetUsernameFingerprint : NativeDownloaderMethodFingerprint("getTweetUsername")

internal object GetTweetProfileNameFingerprint : NativeDownloaderMethodFingerprint("getTweetProfileName")

internal object GetTweetUserIdFingerprint : NativeDownloaderMethodFingerprint("getTweetUserId")

internal object GetTweetMediaFingerprint : NativeDownloaderMethodFingerprint("getTweetMedia")

internal object GetImageUrlFFingerprint : NativeDownloaderMethodFingerprint("getImageUrlField")

internal object GetVideoUrlFFingerprint : NativeDownloaderMethodFingerprint("getVideoUrlField")

internal object GetVideoCodecFFingerprint : NativeDownloaderMethodFingerprint("getVideoCodecField")

internal object GetUserNameMethodCaller : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "Ref_ID (Tweet ID)",
            "Name",
            "User Name",
        ),
)

internal object ImageUrlFieldFingeprint : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "MediaEntity.mediaUrl ",
        ),
    customFingerprint = { methodDef, classDef ->
        classDef.endsWith("TweetMediaView;")
    },
)

internal object VideoUrlFieldFingeprint : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "video/mp4",
            "video/webm",
            "application/x-mpegURL",
        ),
    customFingerprint = { methodDef, classDef ->
        methodDef.name == "<clinit>"
    },
)

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class NativeDownloaderHooksPatch :
    BytecodePatch(
        setOf(
            GetTweetClassFingerprint,
            GetTweetIdFingerprint,
            GetTweetUsernameFingerprint,
            GetTweetMediaFingerprint,
            GetUserNameMethodCaller,
            TweetObjectFingerprint,
            GetTweetProfileNameFingerprint,
            GetTweetUserIdFingerprint,
            ImageUrlFieldFingeprint,
            VideoUrlFieldFingeprint,
            GetImageUrlFFingerprint,
            GetVideoUrlFFingerprint,
            GetVideoCodecFFingerprint,
        ),
    ) {
    private fun MutableMethod.changeFirstString(value: String) {
        this.getInstructions().firstOrNull { it.opcode == Opcode.CONST_STRING }?.let { instruction ->
            val register = (instruction as BuilderInstruction21c).registerA
            this.replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
        } ?: throw PatchException("const-string not found for method: ${this.name}")
    }

    private fun MethodFingerprintResult.getMethodName(index: Int): String =
        (this.mutableMethod.getInstruction<ReferenceInstruction>(index).reference as DexBackedMethodReference).name

    override fun execute(context: BytecodeContext) {
        val getTweetObjectResult = TweetObjectFingerprint.result ?: throw PatchException("bruh")

        val tweetObjectClass = getTweetObjectResult.classDef
        val tweetObjectClassName =
            tweetObjectClass
                .toString()
                .removePrefix("L")
                .removeSuffix(";")
                .replace("/", ".")
        GetTweetClassFingerprint.result?.mutableMethod?.changeFirstString(tweetObjectClassName)
            ?: throw GetTweetClassFingerprint.exception
// ------------
        val getIdMethod =
            tweetObjectClass.methods.firstOrNull { mutableMethod ->
                mutableMethod.name == "getId"
            } ?: throw PatchException("getIdMethod not found")
        GetTweetIdFingerprint.result?.mutableMethod?.changeFirstString(getIdMethod.name)
            ?: throw GetTweetIdFingerprint.exception
// ------------
        val getUserNameMethodCaller =
            GetUserNameMethodCaller.result ?: throw PatchException("Could not find UserNameMethodCaller fingerprint")
        var getUsernameMethod = ""
        var getProfileNameMethod = ""
        getUserNameMethodCaller.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str == "Name") {
                getProfileNameMethod = getUserNameMethodCaller.getMethodName(match.index + 1)
            }
            if (str == "User Name") {
                getUsernameMethod = getUserNameMethodCaller.getMethodName(match.index + 1)
            }
        }
        GetTweetUsernameFingerprint.result?.mutableMethod?.changeFirstString(getUsernameMethod)
            ?: throw GetTweetUsernameFingerprint.exception

        GetTweetProfileNameFingerprint.result?.mutableMethod?.changeFirstString(getProfileNameMethod)
            ?: throw GetTweetProfileNameFingerprint.exception
// ------------
        val getTweetUserIdMethod =
            getTweetObjectResult.classDef.methods
                .last {
                    it.returnType.equals("J")
                }.name
        GetTweetUserIdFingerprint.result?.mutableMethod?.changeFirstString(getTweetUserIdMethod)
            ?: throw GetTweetUserIdFingerprint.exception
// ------------
        val getMediaObjectMethod =
            tweetObjectClass.methods.firstOrNull { methodDef ->
                methodDef.implementation
                    ?.instructions
                    ?.map { it.opcode }
                    ?.toList() ==
                    listOf(
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.IGET_OBJECT,
                        Opcode.RETURN_OBJECT,
                    )
            } ?: throw PatchException("getMediaObject not found")
        GetTweetMediaFingerprint.result?.mutableMethod?.changeFirstString(getMediaObjectMethod.name)
            ?: throw GetTweetMediaFingerprint.exception
// ------------
        ImageUrlFieldFingeprint.result ?.let {
            it.scanResult.stringsScanResult!!.matches.forEach { match ->
                if (match.string == "MediaEntity.mediaUrl ") {
                    var imageUrlFieldName =
                        (it.mutableMethod.getInstruction<ReferenceInstruction>(match.index + 3).reference as FieldReference).name

                    GetImageUrlFFingerprint.result?.mutableMethod?.changeFirstString(imageUrlFieldName)
                        ?: throw GetTweetMediaFingerprint.exception
                }
            }
        }

// ------------
        VideoUrlFieldFingeprint.result ?.let {
            var strFields =
                it.classDef.fields.filter {
                    it.type == "Ljava/lang/String;"
                }

            GetVideoUrlFFingerprint.result?.mutableMethod?.changeFirstString(strFields[0].name)
                ?: throw GetVideoUrlFFingerprint.exception
            GetVideoCodecFFingerprint.result?.mutableMethod?.changeFirstString(strFields[1].name)
                ?: throw GetVideoCodecFFingerprint.exception
        }

        // end
    }
}
