package app.crimera.patches.twitter.models

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.getMethodName
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val tweetEntityPatch =
    bytecodePatch(
        description = "For tweet entity reflection",
        use = true,
    ) {
        compatibleWith("com.twitter.android")

        execute {
            getUserNameMethodCaller.stringMatches?.forEach { match ->
                val str = match.string
                if (str == "Name") {
                    val methodName = getUserNameMethodCaller.method.getMethodName(match.index + 1)

                    tweetUsernameFingerprint.method.changeFirstString(methodName)
                }
                if (str == "User Name") {
                    val methodName = getUserNameMethodCaller.method.getMethodName(match.index + 1)
                    tweetUsernameFingerprint.method.changeFirstString(methodName)
                }
            }

// ------------
            val tweetObjectMethods = tweetObjectFingerprint.classDef.methods

            val getTweetUserIdMethod =
                tweetObjectMethods
                    .last {
                        it.returnType == "J"
                    }.name
            tweetUserIdFingerprint.method.changeFirstString(getTweetUserIdMethod)
// ------------
            val getMediaObjectMethod =
                tweetObjectMethods.firstOrNull { methodDef ->
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
            tweetMediaFingerprint.method.changeFirstString(getMediaObjectMethod.name)

            val extMediaListField =
                tweetMediaEntityClassFingerprint.classDef.fields
                    .first { it.type.contains("List") }
                    .name
            tweetMediaFingerprint.method.changeStringAt(1, extMediaListField)

            // ------------
            val getNoteTweetMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType.contains("notetweet") }
                    ?.name
                    ?: throw PatchException("getNoteTweetMethod not found")
            tweetLongTextFingerprint.method.changeFirstString(getNoteTweetMethod)

            val longTextField =
                longTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name
            tweetLongTextFingerprint.method.changeStringAt(1, longTextField)

// ---------------------
            val tweetEntityMethod =
                tweetObjectMethods
                    .lastOrNull { it.returnType.contains("/entity/") }
                    ?.name
                    ?: throw PatchException("getTweetEntityMethod not found")
            tweetShortTextFingerprint.method.changeFirstString(tweetEntityMethod)

// End
// ---------------------
        }
    }
