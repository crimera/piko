package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.utils.changeStringAt


import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val userFriendshipStatusEntity = bytecodePatch(
    description = "Used to decode user friendship status",
) {

   execute {

       NametagResultCardViewSetButtonMethodFingerprint.method.apply {
           val moveResultIndex = instructions.first { it.opcode == Opcode.MOVE_RESULT }.location.index
           val invokeStaticMethodNames = getInstruction(moveResultIndex - 1).methodExtractor()
           GetHelperClassExtensionFingerprint.changeStringAt(0,invokeStaticMethodNames.definingClass)
           GetFollowBackStatusFingerprint.changeStringAt(0,invokeStaticMethodNames.name)
       }
   }
}