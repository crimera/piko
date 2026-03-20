/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.utils.changeStringAt
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val userFriendshipStatusEntity =
    bytecodePatch(
        description = "Used to decode user friendship status",
    ) {

        execute {

            NametagResultCardViewSetButtonMethodFingerprint.method.apply {
                val firstIfNe = indexOfFirstInstruction(Opcode.IF_NE)
                val invokeStaticMethodNames = getInstruction(firstIfNe + 1).methodExtractor()
                GetHelperClassExtensionFingerprint.changeStringAt(0, invokeStaticMethodNames.definingClass)
                GetFollowBackStatusFingerprint.changeStringAt(0, invokeStaticMethodNames.name)
            }
        }
    }
