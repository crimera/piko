package crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

val MethodFingerprint.exception: PatchException
    get() = PatchException("${this.javaClass.name} is not found")

internal abstract class NativeTranslatorMethodFingerprint(
    private val methodName: String,
) : MethodFingerprint(customFingerprint = { methodDef, classDef ->
        methodDef.name == methodName && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
    })

internal object ShortTweetObjectFingerprint : MethodFingerprint(strings = listOf("wrap(...)", "NONE"))

internal object LongTweetObjectFingerprint : MethodFingerprint(strings = listOf("NoteTweet(id=", ", text="))

internal object TweetObjectFingerprint : MethodFingerprint(strings = listOf("https://x.com/%1\$s/status/%2\$d"))

internal object TweetinfoObjectFingerprint : MethodFingerprint(
    strings =
        listOf(
            "flags",
            "lang",
            "supplemental_language",
        ),
    customFingerprint = { methodDef, classDef ->
        methodDef.parameters.size == 2 && classDef.contains("/tdbh/")
    },
)

internal object GetShortTextMtdFingerprint : NativeTranslatorMethodFingerprint("getShortTextMethodName")

internal object GetLongTextMtdFingerprint : NativeTranslatorMethodFingerprint("getLongTextMethodName")

internal object GetLongTextFldFingerprint : NativeTranslatorMethodFingerprint("getLongTextFieldName")

internal object GetTweetInfoFldFingerprint : NativeTranslatorMethodFingerprint("getTweetInfoField")

internal object GetLangFldFingerprint : NativeTranslatorMethodFingerprint("getLangField")

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
class NativeTranslatorHooksPatch :
    BytecodePatch(
        setOf(
            GetShortTextMtdFingerprint,
            GetLongTextMtdFingerprint,
            GetLongTextFldFingerprint,
            TweetObjectFingerprint,
            TweetinfoObjectFingerprint,
            ShortTweetObjectFingerprint,
            LongTweetObjectFingerprint,
            GetTweetInfoFldFingerprint,
            GetLangFldFingerprint,
        ),
    ) {
    private fun MutableMethod.changeFirstString(value: String) {
        this.getInstructions().firstOrNull { it.opcode == Opcode.CONST_STRING }?.let { instruction ->
            this.replaceInstruction(instruction.location.index, "const-string v0, \"$value\"")
        } ?: throw PatchException("const-string not found for method: ${this.name}")
    }

    override fun execute(context: BytecodeContext) {
        val getTweetObjectFingerprint = TweetObjectFingerprint.result ?: throw PatchException("TweetObjectFingerprint not found")
        val shortTweetObjectFingerprint =
            ShortTweetObjectFingerprint.result ?: throw PatchException("ShortTweetObjectFingerprint not found")
        val longTweetObjectFingerprint = LongTweetObjectFingerprint.result ?: throw PatchException("LongTweetObjectFingerprint not found")

        val tweetObjectClass = getTweetObjectFingerprint.classDef
        val methods = tweetObjectClass.methods

        methods.forEach {
            if (it.returnType.contains(shortTweetObjectFingerprint.classDef)) {
                GetShortTextMtdFingerprint.result?.mutableMethod?.changeFirstString(it.name)
                    ?: throw GetShortTextMtdFingerprint.exception
            } else if (it.returnType.contains(longTweetObjectFingerprint.classDef)) {
                GetLongTextMtdFingerprint.result?.mutableMethod?.changeFirstString(it.name)
                    ?: throw GetLongTextMtdFingerprint.exception
            }
        }
        val longTweetTextField =
            longTweetObjectFingerprint.classDef.fields
                .first { it.type == "Ljava/lang/String;" }
                .name

        GetLongTextFldFingerprint.result?.mutableMethod?.changeFirstString(longTweetTextField)
            ?: throw GetLongTextFldFingerprint.exception

        TweetinfoObjectFingerprint.result ?.let {
            it.scanResult.stringsScanResult!!.matches.forEach { match ->
                if (match.string == "lang") {
                    val ref = (it.mutableMethod.getInstruction<ReferenceInstruction>(match.index + 1).reference as FieldReference)
                    GetLangFldFingerprint.result?.mutableMethod?.changeFirstString(ref.name)
                        ?: throw GetLangFldFingerprint.exception
                    GetTweetInfoFldFingerprint.result?.mutableMethod?.changeFirstString(
                        tweetObjectClass.fields
                            .first {
                                it.type ==
                                    ref.definingClass
                            }.name,
                    )
                        ?: throw GetTweetInfoFldFingerprint.exception
                }
            }
        }
    }
}
