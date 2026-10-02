package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.PatchException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolverCardinalityTest {
    private data class Candidate(val id: String, val owner: String)

    @Test
    fun `requireExactlyOne returns the candidate or fails closed with all descriptions`() {
        val candidate = Candidate("palette-provider", "Lcom/twitter/android/PaletteProvider;")
        assertEquals(
            candidate,
            requireExactlyOne("NewX palette provider", listOf(candidate)) { it.owner },
        )

        val empty =
            assertFailsWith<PatchException> {
                requireExactlyOne<Candidate>("NewX palette provider", emptyList()) { it.owner }
            }
        assertTrue("found 0" in empty.message.orEmpty(), empty.message.orEmpty())

        val ambiguous =
            assertFailsWith<PatchException> {
                requireExactlyOne(
                    "NewX palette provider",
                    listOf(Candidate("first", "Lfirst;"), Candidate("second", "Lsecond;")),
                ) { candidate -> "${candidate.id}@${candidate.owner}" }
            }
        val ambiguousMessage = ambiguous.message.orEmpty()
        assertTrue("2" in ambiguousMessage, ambiguousMessage)
        assertTrue("first@Lfirst;" in ambiguousMessage, ambiguousMessage)
        assertTrue("second@Lsecond;" in ambiguousMessage, ambiguousMessage)
    }

    @Test
    fun `requireAtMostOne returns at most one candidate or fails closed`() {
        val candidate = Candidate("palette-provider", "Lcom/twitter/android/PaletteProvider;")
        assertNull(
            requireAtMostOne<Candidate>("optional NewX palette provider", emptyList()) { it.owner },
        )
        assertEquals(
            candidate,
            requireAtMostOne("optional NewX palette provider", listOf(candidate)) { it.owner },
        )

        val ambiguous =
            assertFailsWith<PatchException> {
                requireAtMostOne(
                    "optional NewX palette provider",
                    listOf(Candidate("first", "Lfirst;"), Candidate("second", "Lsecond;")),
                ) { candidate -> candidate.id }
            }
        val message = ambiguous.message.orEmpty()
        assertTrue("2" in message, message)
        assertTrue("first" in message, message)
        assertTrue("second" in message, message)
    }
}
