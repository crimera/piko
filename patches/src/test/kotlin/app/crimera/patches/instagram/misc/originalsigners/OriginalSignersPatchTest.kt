/*
 * Copyright 2026 Hushfacebook contributors
 * https://github.com/SysAdminDoc/Hushfacebook
 *
 * Modified for HushGram (Instagram), 2026.
 * Adapted for Piko, 2026.
 */

package app.crimera.patches.instagram.misc.originalsigners

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OriginalSignersPatchTest {
    private fun reader(registers: Int, flags: Int = AccessFlags.PUBLIC.value) = MutableMethod(
        ImmutableMethod(
            "Lfixture/Reader;",
            "signers",
            emptyList(),
            "Lfixture/Signers;",
            flags,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(registers, emptyList(), emptyList(), emptyList()),
        ),
    ).apply { addInstructions(0, "const/4 v0, 0x0\nreturn-object v0") }

    @Test
    fun `fallback retains this for ordinary and high parameter registers`() {
        for (registers in listOf(4, 6, 20)) {
            val method = reader(registers)
            method.answerOriginalSigners("packageInfo")
            val body = method.implementation!!.instructions.toList()
            assertEquals(Opcode.MOVE_OBJECT_FROM16, body[0].opcode)
            assertEquals(listOf(0, registers - 1), body[0].registersUsed)
            assertEquals(listOf(0, 0), body[1].registersUsed)
            assertEquals("Landroid/content/pm/PackageInfo;", body[1].getReference<FieldReference>()!!.type)
            assertEquals(Opcode.IF_EQZ, body[4].opcode)
            val branchAddress = body.take(4).sumOf { it.codeUnits }
            val targetAddress = branchAddress + (body[4] as OffsetInstruction).codeOffset
            assertEquals(body.take(9).sumOf { it.codeUnits }, targetAddress)
            assertEquals(Opcode.RETURN_OBJECT, body[8].opcode)
            assertEquals(Opcode.CONST_4, body[9].opcode)
            assertEquals(registers, method.implementation!!.registerCount)
        }
    }

    @Test
    fun `rejects methods whose parameter would be overwritten`() {
        for (registers in 1..3) {
            assertFailsWith<PatchException> { reader(registers).answerOriginalSigners("packageInfo") }
        }
        assertFailsWith<PatchException> {
            reader(6, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value).answerOriginalSigners("packageInfo")
        }
    }
}
