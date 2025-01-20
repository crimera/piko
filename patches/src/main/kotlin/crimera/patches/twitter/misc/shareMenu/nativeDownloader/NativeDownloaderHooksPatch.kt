package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.Match
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

val GetTweetClassFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetClass" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }
val GetTweetIdFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetId" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }

val TweetObjectFingerprint =
    fingerprint {
        strings("https://x.com/%1\$s/status/%2\$d")
    }

val GetTweetUsernameFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetUsername" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }

val GetTweetProfileNameFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetProfileName" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }

val GetTweetUserIdFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetUserId" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }

val GetTweetMediaFingerprint =
    fingerprint {
        custom { methodDef, classDef ->
            methodDef.name == "getTweetMedia" && classDef.toString() == "Lapp/revanced/integrations/twitter/patches/NativeDownloader;"
        }
    }

internal val GetUserNameMethodCaller =
    fingerprint {
        strings(
            "Ref_ID (Tweet ID)",
            "Name",
            "User Name",
        )
        returns("V")
    }

@Suppress("unused")
val nativeDownloaderHooksPatch =
    bytecodePatch {
        compatibleWith("com.twitter.android")

        fun MutableMethod.changeFirstString(value: String) {
            this.instructions.firstOrNull { it.opcode == Opcode.CONST_STRING }?.let { instruction ->
                val register = (instruction as BuilderInstruction21c).registerA
                this.replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
            } ?: throw PatchException("const-string not found for method: ${this.name}")
        }

        fun Match.getMethodName(index: Int): String =
            (this.mutableMethod.getInstruction<ReferenceInstruction>(index).reference as DexBackedMethodReference).name

        execute {
            val getTweetObjectResult by TweetObjectFingerprint()

            val tweetObjectClass = getTweetObjectResult.classDef
            val tweetObjectClassName = tweetObjectClass.toString().removePrefix("L").removeSuffix(";")

            val getIdMethod =
                tweetObjectClass.methods.firstOrNull { mutableMethod ->
                    mutableMethod.name == "getId"
                } ?: throw PatchException("getIdMethod not found")

            val getUserNameMethodCaller by GetUserNameMethodCaller()

            var getUsernameMethod = ""
            var getProfileNameMethod = ""
            getUserNameMethodCaller.stringMatches?.forEach { match ->
                val str = match.string
                if (str == "Name") {
                    getProfileNameMethod = getUserNameMethodCaller.getMethodName(match.index + 1)
                }
                if (str == "User Name") {
                    getUsernameMethod = getUserNameMethodCaller.getMethodName(match.index + 1)
                }
            }

            val getTweetUserIdMethod =
                getTweetObjectResult.classDef.methods
                    .last {
                        it.returnType.equals("J")
                    }.name

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

            GetTweetClassFingerprint.match?.mutableMethod?.changeFirstString(tweetObjectClassName)
                ?: throw GetTweetClassFingerprint.exception

            GetTweetIdFingerprint.match?.mutableMethod?.changeFirstString(getIdMethod.name)
                ?: throw GetTweetIdFingerprint.exception

            GetTweetUsernameFingerprint.match?.mutableMethod?.changeFirstString(getUsernameMethod)
                ?: throw GetTweetUsernameFingerprint.exception

            GetTweetProfileNameFingerprint.match?.mutableMethod?.changeFirstString(getProfileNameMethod)
                ?: throw GetTweetProfileNameFingerprint.exception

            GetTweetUserIdFingerprint.match?.mutableMethod?.changeFirstString(getTweetUserIdMethod)
                ?: throw GetTweetUserIdFingerprint.exception

            GetTweetMediaFingerprint.match?.mutableMethod?.changeFirstString(getMediaObjectMethod.name)
                ?: throw GetTweetMediaFingerprint.exception
        }
    }
