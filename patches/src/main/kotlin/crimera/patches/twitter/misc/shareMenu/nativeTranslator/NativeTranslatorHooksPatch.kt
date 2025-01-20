package crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.TweetObjectFingerprint

val ShortTweetObjectFingerprint =
    fingerprint {
        strings("wrap(...)", "NONE")
    }

val LongTweetObjectFingerprint =
    fingerprint {
        strings("NoteTweet(id=", ", text=")
    }

val TweetinfoObjectFingerprint =
    fingerprint {
        strings(
            "flags",
            "lang",
            "supplemental_language",
        )
        custom { methodDef, _ ->
            methodDef.parameters.size == 2
        }
    }

val GetShortTextMtdFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getShortTextMethodName" &&
                classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
        }
    }

val GetLongTextMtdFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getLongTextMethodName" &&
                classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
        }
    }

val GetLongTextFldFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getLongTextFieldName" &&
                classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
        }
    }

val GetTweetInfoFldFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetInfoField" &&
                classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
        }
    }

val GetLangFldFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getLangField" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/translator/Constants;"
        }
    }

val nativeTranslatorHooksPatch =
    bytecodePatch {
        compatibleWith("com.twitter.android")

        fun MutableMethod.changeFirstString(value: String) {
            this.instructions.firstOrNull { it.opcode == Opcode.CONST_STRING }?.let { instruction ->
                this.replaceInstruction(instruction.location.index, "const-string v0, \"$value\"")
            } ?: throw PatchException("const-string not found for method: ${this.name}")
        }
        execute {
            val getTweetObjectFingerprint by TweetObjectFingerprint()
            val shortTweetObjectFingerprint by ShortTweetObjectFingerprint()
            val longTweetObjectFingerprint by LongTweetObjectFingerprint()

            val tweetObjectClass = getTweetObjectFingerprint.classDef
            val methods = tweetObjectClass.methods

            methods.forEach {
                if (it.returnType.contains(shortTweetObjectFingerprint.classDef)) {
                    GetShortTextMtdFingerprint.match?.mutableMethod?.changeFirstString(it.name)
                } else if (it.returnType.contains(longTweetObjectFingerprint.classDef)) {
                    GetLongTextMtdFingerprint.match?.mutableMethod?.changeFirstString(it.name)
                }
            }
            val longTweetTextField =
                longTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name

            GetLongTextFldFingerprint.match?.mutableMethod?.changeFirstString(longTweetTextField)

            TweetinfoObjectFingerprint.match ?.let {
                it.stringMatches?.forEach { match ->
                    if (match.string == "lang") {
                        val ref = (it.mutableMethod.getInstruction<ReferenceInstruction>(match.index + 1).reference as FieldReference)
                        GetLangFldFingerprint.match?.mutableMethod?.changeFirstString(ref.name)

                        GetTweetInfoFldFingerprint.match?.mutableMethod?.changeFirstString(
                            tweetObjectClass.fields
                                .first {
                                    it.type ==
                                        ref.definingClass
                                }.name,
                        )
                    }
                }
            }
        }
    }
