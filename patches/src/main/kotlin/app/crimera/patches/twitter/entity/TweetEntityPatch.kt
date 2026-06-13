/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object TweetEntityFingerprint : Fingerprint(
    filters =
        listOf(
            string("extended_entities"),
        ),
)

val tweetEntityPatch =
    bytecodePatch(
        name = "Tweet entity",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            val classDef = TweetEntityFingerprint.classDef
            val fields = classDef.fields
            val methods = classDef.methods

            val listField = fields.firstOrNull { it.type.contains("List") }
                ?: throw PatchException("Failed to find List field in ${classDef.type}")
            val stringField = fields.firstOrNull { it.type == "Ljava/lang/String;" }
                ?: throw PatchException("Failed to find String field in ${classDef.type}")
            val longMethod = methods.firstOrNull { it.returnType == "J" }
                ?: throw PatchException("Failed to find Long method in ${classDef.type}")

            val newInstanceIndex = longMethod.instructions.firstOrNull { it.opcode == Opcode.NEW_INSTANCE }?.location?.index
                ?: throw PatchException("Failed to find NEW_INSTANCE in longMethod")

            val lastInvokeVirtualRange = longMethod.instructions.lastOrNull {
                it.opcode == Opcode.INVOKE_VIRTUAL_RANGE && it.location.index < newInstanceIndex
            } ?: throw PatchException("Failed to find INVOKE_VIRTUAL_RANGE before NEW_INSTANCE in longMethod")

            longMethod.replaceInstruction(
                lastInvokeVirtualRange.location.index,
                "invoke-static {p0, v0, v1, v2, v3, v4}, Lapp/crimera/piko/patches/twitter/EntityPatch;->tweetEntity(Ljava/lang/Object;JJJJ)V",
            )
        }
    }
