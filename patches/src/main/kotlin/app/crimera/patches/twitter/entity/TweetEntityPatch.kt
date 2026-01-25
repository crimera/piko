package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.getMethodName
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val tweetEntityPatch =
    bytecodePatch(
        description = "For tweet entity reflection",
    ) {
        execute {
            getUserNameMethodCaller.stringMatches?.forEach { match ->
                val str = match.string
                if (str == "Name") {
                    val methodName = getUserNameMethodCaller.getMethodName(match.index + 1)
                    tweetProfileNameFingerprint.changeFirstString(methodName)
                }
                if (str == "User Name") {
                    val methodName = getUserNameMethodCaller.getMethodName(match.index + 1)
                    tweetUsernameFingerprint.changeFirstString(methodName)
                }
            }

// ------------
            val tweetObjectMethods = tweetObjectFingerprint.classDef.methods

            val getTweetUserIdMethod =
                tweetObjectMethods
                    .last {
                        it.returnType == "J"
                    }.name
            tweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)
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
            tweetMediaFingerprint.changeFirstString(getMediaObjectMethod.name)

            val extMediaListField =
                tweetMediaEntityClassFingerprint.classDef.fields
                    .first { it.type.contains("List") }
                    .name
            tweetMediaFingerprint.changeStringAt(1, extMediaListField)

            // ------------
            val getNoteTweetMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType.contains("notetweet") }
                    ?.name
                    ?: throw PatchException("getNoteTweetMethod not found")
            tweetLongTextFingerprint.changeFirstString(getNoteTweetMethod)

            val longTextField =
                longTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name
            tweetLongTextFingerprint.changeStringAt(1, longTextField)

            quotedViewSetAccessibilityFingerprint.method.apply {
                val newInstanceIndex = indexOfFirstInstruction(Opcode.NEW_INSTANCE)
                val invokeVirtualRangeInst =
                    instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL_RANGE && it.location.index < newInstanceIndex }
                tweetShortTextFingerprint
                    .changeFirstString(quotedViewSetAccessibilityFingerprint.getMethodName(invokeVirtualRangeInst.location.index))
            }

// End
// ---------------------
        }
    }
