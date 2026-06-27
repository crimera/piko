/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.getMethodName
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val tweetEntityPatch =
    bytecodePatch(
        description = "For tweet entity reflection",
    ) {
        execute {
            val (profileNameMethod, userNameMethod) =
                with(TweetNamesFingerprint) {
                    Pair(
                        instructionMatches[0].instruction.getReference<MethodReference>()!!,
                        instructionMatches[2].instruction.getReference<MethodReference>()!!,
                    )
                }

            TweetUsernameFingerprint.changeFirstString(userNameMethod.name)
            TweetProfileNameFingerprint.changeFirstString(profileNameMethod.name)

            val tweetObjectMethods = TweetObjectFingerprint.classDef.methods

            val getTweetUserIdMethod =
                tweetObjectMethods
                    .first { it.returnType == "J" }
                    .name
            TweetUserIdFingerprint.changeFirstString(getTweetUserIdMethod)

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
            TweetMediaFingerprint.changeFirstString(getMediaObjectMethod.name)

            val extMediaListField =
                TweetMediaEntityClassFingerprint.classDef.fields
                    .first { it.type.contains("List") }
                    .name
            TweetMediaFingerprint.changeStringAt(1, extMediaListField)

            val getNoteTweetMethod =
                tweetObjectMethods
                    .firstOrNull { it.returnType.contains("notetweet") }
                    ?.name
                    ?: throw PatchException("getNoteTweetMethod not found")
            TweetLongTextFingerprint.changeFirstString(getNoteTweetMethod)

            val longTextField =
                LongTweetObjectFingerprint.classDef.fields
                    .first { it.type == "Ljava/lang/String;" }
                    .name
            TweetLongTextFingerprint.changeStringAt(1, longTextField)

            QuotedViewSetAccessibilityFingerprint.method.apply {
                val newInstanceIndex = indexOfFirstInstruction(Opcode.NEW_INSTANCE)
                val invokeVirtualRangeInst =
                    instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL_RANGE && it.location.index < newInstanceIndex }
                TweetShortTextFingerprint
                    .changeFirstString(
                        QuotedViewSetAccessibilityFingerprint.getMethodName(
                            invokeVirtualRangeInst.location.index,
                        ),
                    )
            }
        }
    }
