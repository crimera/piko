package app.crimera.patches.newx.misc.navbar

// Guards the NewX tab-map wrapper resolver against constructor drift. On 12.33.0-prod.01 the landing
// wrapper gained an int between its Map and flag parameters; the exact parameter-list match found zero
// candidates and failed the "Customize navigation bar" patch.

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavBarTabDataWrapperTest {
    // Map.put on v5 is the anchor; the wrapper must receive that same v5 as its LinkedHashMap.
    private val mapPut = "invoke-interface {v5, v6, v1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
    private val prodWrapper =
        "invoke-direct {v4, v6, v0, v0, v5}, Lcom/x/main/api/landing/j;-><init>(Ljava/util/Map;IZLjava/util/LinkedHashMap;)V"
    private val legacyWrapper =
        "invoke-direct {v4, v6, v0, v5}, Lcom/x/main/api/landing/j;-><init>(Ljava/util/Map;ZLjava/util/LinkedHashMap;)V"

    @Test
    fun `wrapper resolves across the 12_29 and 12_33 prod constructor shapes`() {
        assertEquals(2, method(mapPut, "sget-object v6, Lkotlin/collections/h;->a:Lkotlin/collections/h;", prodWrapper)
            .findTabDataWrapperInitIndex(anchorIndex = 0, mapRegister = 5))
        assertEquals(1, method(mapPut, legacyWrapper)
            .findTabDataWrapperInitIndex(anchorIndex = 0, mapRegister = 5))
    }

    @Test
    fun `wrapper failures stay fail-closed`() {
        // A LinkedHashMap constructor fed from another register is a different map, not the tab map.
        val otherMap = "invoke-direct {v4, v6, v0, v0, v3}, Lcom/x/main/api/landing/j;-><init>(Ljava/util/Map;IZLjava/util/LinkedHashMap;)V"
        val missing = assertFailsWith<PatchException> {
            method(mapPut, otherMap).findTabDataWrapperInitIndex(anchorIndex = 0, mapRegister = 5)
        }
        assertTrue(missing.message.orEmpty().contains("found 0"), missing.message)

        val ambiguous = assertFailsWith<PatchException> {
            method(mapPut, prodWrapper, legacyWrapper)
                .findTabDataWrapperInitIndex(anchorIndex = 0, mapRegister = 5)
        }
        assertTrue(ambiguous.message.orEmpty().contains("found 2"), ambiguous.message)
    }

    /** Builds a void static method from smali lines, terminated by return-void. */
    private fun method(vararg lines: String): MutableMethod {
        val implementation = MethodImplementationBuilder(10)
        lines.forEach { implementation.addInstruction(it.toInstruction()) }
        implementation.addInstruction("return-void".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/NavBarTabDataWrapperFixture;",
                "builder",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }
}
