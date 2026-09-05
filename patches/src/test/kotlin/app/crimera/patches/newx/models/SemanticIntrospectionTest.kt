package app.crimera.patches.newx.models

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SemanticIntrospectionTest {
    private val owner = "Lcom/twitter/android/obfuscated/Post;"

    private fun field(name: String, type: String): FieldReference =
        object : FieldReference {
            override fun getDefiningClass(): String = owner
            override fun getName(): String = name
            override fun getType(): String = type
            override fun toString(): String = "$owner->$name:$type"
            override fun compareTo(other: FieldReference): Int =
                toString().compareTo(other.toString())
            override fun validateReference() = Unit
        }

    @Test
    fun `resolves the single plausible candidate`() {
        val candidate = field("entryId", "Ljava/lang/String;")
        assertEquals(
            candidate,
            requireSingleToStringField(", entryId=", owner, listOf(candidate)),
        )
    }

    @Test
    fun `collapses duplicate candidates with the same descriptor`() {
        val candidate = field("entryId", "Ljava/lang/String;")
        assertEquals(
            candidate,
            requireSingleToStringField(", entryId=", owner, listOf(candidate, candidate)),
        )
    }

    @Test
    fun `fails loudly on duplicate descriptors instead of first-match`() {
        val first = field("entryId", "Ljava/lang/String;")
        val second = field("clientEventInfo", "Lcom/twitter/android/obfuscated/Event;")
        val exception = assertFailsWith<PatchException> {
            requireSingleToStringField(", entryId=", owner, listOf(first, second))
        }
        val message = exception.message.orEmpty()
        assertTrue(first.toString() in message, "missing '$first' in: $message")
        assertTrue(second.toString() in message, "missing '$second' in: $message")
    }

    @Test
    fun `fails loudly on zero candidates`() {
        val exception = assertFailsWith<PatchException> {
            requireSingleToStringField(", entryId=", owner, emptyList())
        }
        assertTrue(", entryId=" in exception.message.orEmpty())
        assertTrue(owner in exception.message.orEmpty())
    }
}