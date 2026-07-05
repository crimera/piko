/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.moreProfileInfo

import app.crimera.patches.twitter.entity.decoder.TWITTER_USER_CLASS_NAME
import app.crimera.patches.twitter.entity.decoder.decoderEntity
import app.crimera.patches.twitter.entity.twitterUser.twitterUserEntity
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val CLASS_NAME = "${PATCHES_DESCRIPTOR}/profile/MoreProfileInfo;"

@Suppress("unused")
val moreProfileInfoPatch =
    bytecodePatch(
        name = "More information on profile",
        description = "Adds more details on the profile page",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, resourceMappingPatch, moreProfileInfoResourcePatch, twitterUserEntity, decoderEntity)
        execute {

            var setTweetViewStatMethodCall: MethodReference
            ProfileStatViewLoaderFingerprint.apply {
                val filterIndex = instructionMatches.first().index
                method.apply {

                    val methodInvokeInstruction = indexOfFirstInstruction(filterIndex, Opcode.INVOKE_STATIC)
                    setTweetViewStatMethodCall = getInstruction(methodInvokeInstruction).getReference<MethodReference>()!!

                    val entityRegister = getInstruction(indexOfFirstInstruction(filterIndex, Opcode.IGET)).registersUsed[1]
                    val headerComponentIGetObjectIndex =
                        instructions.indexOfLast {
                            it.opcode == Opcode.IGET_OBJECT &&
                                it.location.index < filterIndex
                        }
                    val headerComponentInstruction = getInstruction(headerComponentIGetObjectIndex)
                    val actualHCRegister = headerComponentInstruction.registersUsed[1]

                    val ifNezRegister = getInstruction(headerComponentIGetObjectIndex - 1).registersUsed[0]

                    addInstructions(
                        headerComponentIGetObjectIndex,
                        """
                        invoke-static {v$actualHCRegister,v$entityRegister}, $CLASS_NAME->addTweetStatView(Ljava/lang/Object;Ljava/lang/Object;)V  
                        """.trimIndent(),
                    )
                }
                SetTweetStatViewValueExtension.method.addInstructions(
                    0,
                    """
                    invoke-static {p0,p1,p2,p3}, $setTweetViewStatMethodCall
                    """.trimIndent(),
                )
            }

            val profileStatFormerClass = mutableClassDefBy { it.type == ProfileStatViewFormerFingerprint.method.returnType }
            val fields = profileStatFormerClass.fields

            HeaderComponentContextFieldNameExtension.changeFirstString(fields.first { it.type == "Landroid/content/Context;" }.name)
            HeaderComponentViewFieldNameExtension.changeFirstString(fields.first { it.type == "Landroid/view/View;" }.name)

            val userHeaderFields = SetUserNameOnUserHeaderFingerprint.classDef.fields
            val profileInfoField = userHeaderFields.first { it.type.startsWith("Lcom/twitter/profiles/") }
            val userFieldFromProfileInfo =
                mutableClassDefBy { it.type == profileInfoField.type }.fields.first {
                    it.type ==
                        TWITTER_USER_CLASS_NAME
                }

            SetUserNameOnUserHeaderFingerprint.method.apply {
                val userNameObjectIndex = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                val userNameStringRegister = getInstruction(userNameObjectIndex).registersUsed[0]

                addInstructions(
                    userNameObjectIndex + 1,
                    """
                       # final lines of method so hardcoding registers
                    iget-object v1, p0, $profileInfoField
                    iget-object v1, v1, $userFieldFromProfileInfo
                    
                    invoke-static {p1,v1}, $CLASS_NAME->addUserId(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object p1

                    """.trimIndent(),
                )
            }

            enableSettings("moreInfoOnProfile")
        }
    }
