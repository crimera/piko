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
    fun `requireExactlyOne returns the only candidate`() {
        val candidate = Candidate("palette-provider", "Lcom/twitter/android/PaletteProvider;")

        assertEquals(
            candidate,
            requireExactlyOne("NewX palette provider", listOf(candidate)) { it.owner },
        )
    }

    @Test
    fun `requireExactlyOne reports an empty candidate set`() {
        val exception =
            assertFailsWith<PatchException> {
                requireExactlyOne<Candidate>("NewX palette provider", emptyList()) { it.owner }
            }

        assertEquals(
            "Expected exactly one NewX palette provider, found 0: []",
            exception.message,
        )
    }

    @Test
    fun `requireExactlyOne reports every candidate description when ambiguous`() {
        val first = Candidate("first", "Lfirst;")
        val second = Candidate("second", "Lsecond;")

        val exception =
            assertFailsWith<PatchException> {
                requireExactlyOne(
                    "NewX palette provider",
                    listOf(first, second),
                ) { candidate -> "${candidate.id}@${candidate.owner}" }
            }

        val message = exception.message.orEmpty()
        assertTrue("NewX palette provider" in message)
        assertTrue("2" in message)
        assertTrue("first@Lfirst;" in message)
        assertTrue("second@Lsecond;" in message)
    }

    @Test
    fun `requireAtMostOne accepts no candidate`() {
        assertNull(
            requireAtMostOne<Candidate>("optional NewX palette provider", emptyList()) { it.owner },
        )
    }

    @Test
    fun `requireAtMostOne returns the only candidate`() {
        val candidate = Candidate("palette-provider", "Lcom/twitter/android/PaletteProvider;")

        assertEquals(
            candidate,
            requireAtMostOne("optional NewX palette provider", listOf(candidate)) { it.owner },
        )
    }

    @Test
    fun `requireAtMostOne reports every candidate description when ambiguous`() {
        val first = Candidate("first", "Lfirst;")
        val second = Candidate("second", "Lsecond;")

        val exception =
            assertFailsWith<PatchException> {
                requireAtMostOne(
                    "optional NewX palette provider",
                    listOf(first, second),
                ) { candidate -> candidate.id }
            }

        val message = exception.message.orEmpty()
        assertTrue("optional NewX palette provider" in message)
        assertTrue("2" in message)
        assertTrue("first" in message)
        assertTrue("second" in message)
    }
}
