package app.crimera.tools.newx

import app.crimera.patches.newx.misc.canonicalurls.constructsProfileHeaderModel
import app.crimera.patches.newx.misc.serverlogging.RegisterLocation
import app.crimera.patches.newx.misc.serverlogging.selectSubmitFailureOperation
import app.crimera.patches.newx.timeline.isNewPostButtonRendererCandidate
import app.crimera.patches.newx.timeline.timelineModuleDividerItemIndices
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NewXResolverLinterTest {
    @Test
    fun `new-post resolver survives null-check churn and rejects modifier lookalike`() {
        val controlRenderer = newPostRendererFixture(namedNullCheck = true, directModifier = true)
        val controlLookalike = newPostRendererFixture(namedNullCheck = true, directModifier = false)
        val targetRenderer = newPostRendererFixture(namedNullCheck = false, directModifier = true)
        val targetLookalike = newPostRendererFixture(namedNullCheck = false, directModifier = false)

        assertEquals(
            listOf(true, false),
            listOf(controlRenderer, controlLookalike).map(Method::isNewPostButtonRendererCandidate),
        )
        assertEquals(
            listOf(true, false),
            listOf(targetRenderer, targetLookalike).map(Method::isNewPostButtonRendererCandidate),
        )
    }

    @Test
    fun `new-post resolver survives 12-29 compose-flag ABI and conditional default modifier`() {
        val renderer = newPostRendererComposeFlagFixture(directModifier = true)
        val lookalike = newPostRendererComposeFlagFixture(directModifier = false)

        assertEquals(
            listOf(true, false),
            listOf(renderer, lookalike).map(Method::isNewPostButtonRendererCandidate),
        )
    }

    @Test
    fun `raw candidate selection is rejected`() {
        val findings = lint("val selected = candidates.first()")

        assertEquals(listOf(NewXResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
    }

    @Test
    fun `raw find and last are rejected`() {
        val findings =
            lint(
                """
                val selected = methods.find { it.name == "target" }
                val other = fields.last()
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                NewXResolverLinter.Rule.RAW_FIND,
                NewXResolverLinter.Rule.RAW_LAST,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `explicit cardinality guard permits raw first`() {
        val findings =
            lint(
                """
                if (candidates.size != 1) throw IllegalStateException()
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `explicit cardinality guard permits indexed access with nullable failure`() {
        val findings =
            lint(
                """
                fun resolve(candidates: List<String>): String? {
                    if (candidates.size != 1) return null
                    return candidates[0]
                }
                fun isTarget(candidates: List<String>): Boolean {
                    if (candidates.size != 1) return false
                    return candidates[0].isNotEmpty()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `unguarded indexed candidate access is rejected`() {
        val findings = lint("val selected = candidates[0]")

        assertEquals(listOf(NewXResolverLinter.Rule.RAW_INDEX), findings.map { it.rule })
    }

    @Test
    fun `parameter type indexed access is not a resolver candidate`() {
        val findings = lint("val parameterType = parameterTypes[0]")

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `at most one proof does not permit required candidate selection`() {
        val findings =
            lint(
                """
                if (candidates.size > 1) throw IllegalStateException()
                val first = candidates.first()
                val last = candidates.last()
                val found = candidates.find { it.isTarget }
                val single = candidates.single()
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                NewXResolverLinter.Rule.RAW_FIRST,
                NewXResolverLinter.Rule.RAW_LAST,
                NewXResolverLinter.Rule.RAW_FIND,
                NewXResolverLinter.Rule.RAW_SINGLE,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `singleOrNull safe call is rejected as nullable fallthrough`() {
        val findings = lint("candidates.singleOrNull()?.patch()")

        assertEquals(listOf(NewXResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
    }

    @Test
    fun `singleOrNull with throwing fallback is accepted`() {
        val findings =
            lint(
                "val selected = candidates.singleOrNull() ?: throw IllegalStateException()",
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `nullable selection followed by a returning null branch is rejected`() {
        val findings =
            lint(
                """
                val selected = candidates.singleOrNull()
                if (selected == null) return
                selected.patch()
                """.trimIndent(),
            )

        assertEquals(listOf(NewXResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
    }

    @Test
    fun `nullable first and last are rejected when they fall through`() {
        val findings =
            lint(
                """
                val first = matches.firstOrNull()?.patch()
                val last = matches.lastOrNull() ?: return
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                NewXResolverLinter.Rule.NULLABLE_FIRST,
                NewXResolverLinter.Rule.NULLABLE_LAST,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `at most one guard permits optional singleOrNull`() {
        val findings =
            lint(
                """
                if (matches.size > 1) throw IllegalStateException()
                matches.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `shared cardinality helpers prove subsequent selections`() {
        val findings =
            lint(
                """
                requireExactlyOne("required candidate", candidates)
                val selected = candidates.first()
                requireAtMostOne("optional candidate", matches)
                val optional = matches.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `mapNotNull candidate dropping is reported at the map`() {
        val findings =
            lint(
                """
                val candidates = methods.mapNotNull { method ->
                    if (method.name == "target") method else return@mapNotNull null
                }
                candidates.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.any { it.rule == NewXResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
        assertTrue(findings.size == 2, "actual=$findings")
        assertEquals(1, findings.first { it.rule == NewXResolverLinter.Rule.MAP_NOT_NULL }.line)
    }

    @Test
    fun `implicit mapNotNull filtering is also reported`() {
        val findings =
            lint(
                """
                val selected = methods.mapNotNull { it.takeIf { method -> method.isTarget } }.first()
                """.trimIndent(),
            )

        assertTrue(findings.any { it.rule == NewXResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
    }

    @Test
    fun `mapNotNull with exact guard is accepted`() {
        val findings =
            lint(
                """
                val candidates = instructions.mapNotNull { instruction ->
                    instruction.takeIf { it.isTarget } ?: return@mapNotNull null
                }
                if (candidates.size != 1) throw IllegalStateException()
                candidates.single()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `instruction order scans are allowed`() {
        val findings =
            lint(
                """
                val firstInstruction = method.instructions.first()
                val lastInstruction = match.instructionMatches.last()
                val register = instruction.registersUsed.firstOrNull()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `bounded lookup over instruction candidates is allowed`() {
        val findings =
            lint(
                """
                val pageLookupCandidates = instructions.withIndex().filter { it.value.isLookup }
                val previous = pageLookupCandidates.lastOrNull { candidateIndex < index }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `comments and strings are ignored`() {
        val findings =
            lint(
                """
                // val selected = candidates.first()
                val documentation = "methods.last() and candidates.find { true }"
                /* candidates.singleOrNull()?.patch() */
                val selected = instructions.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `directive-looking text in a string does not suppress findings`() {
        val findings =
            lint(
                """
                val documentation = "newx-resolver-lint: allow raw-first"
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertEquals(listOf(NewXResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
    }

    @Test
    fun `line directives can document an exceptional order choice`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow raw-first because bytecode order is the contract
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `lintDirectory is deterministic and only scans Kotlin files`() {
        val root = Files.createTempDirectory("newx-resolver-linter")
        try {
            root.resolve("z/Later.kt").apply {
                parent.createDirectories()
                writeText("val selected = candidates.last()")
            }
            root.resolve("a/Earlier.kt").apply {
                parent.createDirectories()
                writeText("val selected = candidates.first()")
            }
            root.resolve("Ignored.java").writeText("val selected = candidates.first()")

            val firstRun = NewXResolverLinter.lintDirectory(root)
            val secondRun = NewXResolverLinter.lintDirectory(root)

            assertEquals(firstRun, secondRun)
            assertEquals(
                listOf("a/Earlier.kt", "z/Later.kt"),
                firstRun.map { it.path },
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `lintDirectory rejects a source root without Kotlin files`() {
        val root = Files.createTempDirectory("newx-resolver-linter-empty")
        try {
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    NewXResolverLinter.lintDirectory(root)
                }

            assertTrue(exception.message.orEmpty().contains("No Kotlin NewX resolver sources"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `module divider selection accepts non-foundation inline adapter and scopes discriminator`() {
        val instructions = moduleDividerBuilderFixture()

        val selected =
            instructions.timelineModuleDividerItemIndices { adapterType, discriminator ->
                dividerAdapterCaseBlock(adapterType, discriminator)
            }

        assertEquals(listOf(8), selected)
    }

    @Test
    fun `module divider selection rejects hoisted and non-divider lambda items`() {
        val instructions = moduleDividerBuilderFixture()

        val selected =
            instructions.timelineModuleDividerItemIndices { _, _ ->
                listOf("return-void".toInstruction())
            }

        assertTrue(selected.isEmpty(), selected.toString())
    }

    @Test
    fun `module divider selection follows null key through object moves`() {
        val instructions =
            listOf(
                // 12.29 lowers the null reference key to a zero constant that reaches the lazy call
                // through move-object aliases, so the direct-const window alone misses the divider.
                "const/4 v0, 0x0".toInstruction(),
                "move-object v1, v0".toInstruction(),
                "new-instance v2, Lfixture/DividerAdapter;".toInstruction(),
                "const/16 v3, 0x7".toInstruction(),
                "invoke-direct {v2, v4, v3}, Lfixture/DividerAdapter;-><init>(Ljava/lang/Object;I)V".toInstruction(),
                "new-instance v5, Landroidx/compose/runtime/internal/f;".toInstruction(),
                "const v6, 0x1".toInstruction(),
                "invoke-direct {v5, v2, v7, v6}, Landroidx/compose/runtime/internal/f;-><init>(Ljava/lang/Object;ZI)V".toInstruction(),
                "const/4 v8, 0x3".toInstruction(),
                "move-object v9, v1".toInstruction(),
                "invoke-static {v10, v9, v5, v8}, Landroidx/compose/foundation/lazy/k;->u(Landroidx/compose/foundation/lazy/k;Ljava/lang/Object;Lkotlin/jvm/functions/Function3;I)V".toInstruction(),
            )

        val selected =
            instructions.timelineModuleDividerItemIndices { adapterType, discriminator ->
                dividerAdapterCaseBlock(adapterType, discriminator)
            }

        assertEquals(listOf(10), selected)
    }

    @Test
    fun `profile link resolver accepts profile-header construction and rejects URL-entity copies`() {
        val profileHeader = profileLinkFixture(constructsProfileHeader = true)
        val urlEntityCopy = profileLinkFixture(constructsProfileHeader = false)

        assertEquals(
            listOf(true, false),
            listOf(profileHeader, urlEntityCopy).map(Method::constructsProfileHeaderModel),
        )
    }

    @Test
    fun `submit failure operation selection handles absent POST_SUCCESS event`() {
        val failureComparison = RegisterLocation(index = 20, register = 9, branchTargetIndex = 25)
        val successComparison = RegisterLocation(index = 40, register = 5, branchTargetIndex = 60)

        assertEquals(
            failureComparison,
            selectSubmitFailureOperation(
                laterOperationCandidates = listOf(failureComparison, successComparison),
                successEventIndex = null,
            ),
        )
        assertEquals(
            failureComparison,
            selectSubmitFailureOperation(
                laterOperationCandidates = listOf(failureComparison, successComparison),
                successEventIndex = 45,
            ),
        )
        assertFailsWith<PatchException> {
            selectSubmitFailureOperation(
                laterOperationCandidates = listOf(failureComparison),
                successEventIndex = null,
            )
        }
    }

    private fun moduleDividerBuilderFixture(): List<Instruction> =
        listOf(
            // Divider item: a non-foundation adapter discriminator whose case block draws a divider.
            "new-instance v1, Lfixture/DividerAdapter;".toInstruction(),
            "const/16 v2, 0x7".toInstruction(),
            "invoke-direct {v1, v3, v2}, Lfixture/DividerAdapter;-><init>(Ljava/lang/Object;I)V".toInstruction(),
            "new-instance v4, Landroidx/compose/runtime/internal/f;".toInstruction(),
            "const v5, 0x1".toInstruction(),
            "invoke-direct {v4, v1, v6, v5}, Landroidx/compose/runtime/internal/f;-><init>(Ljava/lang/Object;ZI)V".toInstruction(),
            "const/4 v7, 0x3".toInstruction(),
            "const/4 v0, 0x0".toInstruction(),
            "invoke-static {v8, v0, v4, v7}, Landroidx/compose/foundation/lazy/k;->u(Landroidx/compose/foundation/lazy/k;Ljava/lang/Object;Lkotlin/jvm/functions/Function3;I)V".toInstruction(),
            // Sibling item: same adapter, different discriminator that does not draw a divider.
            "new-instance v11, Lfixture/DividerAdapter;".toInstruction(),
            "const/16 v12, 0x3".toInstruction(),
            "invoke-direct {v11, v13, v12}, Lfixture/DividerAdapter;-><init>(Ljava/lang/Object;I)V".toInstruction(),
            "new-instance v14, Landroidx/compose/runtime/internal/f;".toInstruction(),
            "const v5, 0x2".toInstruction(),
            "invoke-direct {v14, v11, v6, v5}, Landroidx/compose/runtime/internal/f;-><init>(Ljava/lang/Object;ZI)V".toInstruction(),
            "const/4 v15, 0x3".toInstruction(),
            "const/4 v10, 0x0".toInstruction(),
            "invoke-static {v8, v10, v14, v15}, Landroidx/compose/foundation/lazy/k;->u(Landroidx/compose/foundation/lazy/k;Ljava/lang/Object;Lkotlin/jvm/functions/Function3;I)V".toInstruction(),
            // Zero-key item with a hoisted static lambda that overwrites a stale wrapper register.
            "const/4 v1, 0x0".toInstruction(),
            "sget-object v4, Lfixture/Holder;->hoisted:Landroidx/compose/runtime/internal/f;".toInstruction(),
            "const/4 v3, 0x3".toInstruction(),
            "invoke-static {v8, v1, v4, v3}, Landroidx/compose/foundation/lazy/k;->u(Landroidx/compose/foundation/lazy/k;Ljava/lang/Object;Lkotlin/jvm/functions/Function3;I)V".toInstruction(),
        )

    private fun dividerAdapterCaseBlock(
        adapterType: String,
        discriminator: Int,
    ): List<Instruction>? =
        when {
            adapterType != "Lfixture/DividerAdapter;" -> null
            discriminator == 0x7 ->
                listOf(
                    (
                        "invoke-static/range {v0 .. v6}, Landroidx/compose/material3/x;->f(" +
                            "FIIJLandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)V"
                    ).toInstruction(),
                )
            else -> listOf("return-void".toInstruction())
        }

    private fun profileLinkFixture(constructsProfileHeader: Boolean): Method {
        val implementation = MethodImplementationBuilder(4)
        implementation.addInstruction(
            "iget-object v0, v1, Lfixture/UrlEntity;->displayUrl:Ljava/lang/String;".toInstruction(),
        )
        implementation.addInstruction(
            if (constructsProfileHeader) {
                "new-instance v2, Lcom/x/profile/header/Fixture;".toInstruction()
            } else {
                "new-instance v2, Lfixture/UrlEntityCopy;".toInstruction()
            },
        )
        implementation.addInstruction(
            "iget-object v0, v1, Lfixture/UrlEntity;->url:Ljava/lang/String;".toInstruction(),
        )
        implementation.addInstruction("return-void".toInstruction())
        return ImmutableMethod(
            "Lfixture/ProfileLink;",
            "build",
            emptyList<ImmutableMethodParameter>(),
            "Ljava/lang/Object;",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation.methodImplementation,
        )
    }

    private fun lint(source: String): List<NewXResolverLinter.Finding> =
        NewXResolverLinter.lintSource("Fixture.kt", source)

    private fun newPostRendererComposeFlagFixture(directModifier: Boolean): Method {
        val implementation = MethodImplementationBuilder(14)
        implementation.addInstruction(
            "invoke-virtual {v10}, Ljava/lang/Object;->getClass()Ljava/lang/Class;".toInstruction(),
        )
        implementation.addInstruction(
            "invoke-interface {v2}, Lfixture/Visibility;->isVisible()Z".toInstruction(),
        )
        implementation.addInstruction("move-result v0".toInstruction())
        if (directModifier) {
            // 12.29's default-modifier branch overwrites the Modifier parameter slot (v9 = p0) before
            // the alias, so provenance must short-circuit on reaching the parameter register.
            implementation.addInstruction(
                (
                    "sget-object v9, Lfixture/CompanionModifier;->Companion:" +
                        "Landroidx/compose/ui/Modifier;"
                ).toInstruction(),
            )
            implementation.addInstruction("move-object v1, v9".toInstruction())
        } else {
            implementation.addInstruction(
                (
                    "sget-object v1, Lfixture/CompanionModifier;->Companion:" +
                        "Landroidx/compose/ui/Modifier;"
                ).toInstruction(),
            )
        }
        implementation.addInstruction(
            (
                "invoke-static/range {v0 .. v8}, Landroidx/compose/animation/Fixture;->render(" +
                    "ZLandroidx/compose/ui/Modifier;Lfixture/Enter;Lfixture/Exit;" +
                    "Ljava/lang/String;Lkotlin/jvm/functions/Function3;" +
                    "Landroidx/compose/runtime/Composer;II)V"
            ).toInstruction(),
        )
        implementation.addInstruction("return-void".toInstruction())

        val parameters =
            listOf(
                "Landroidx/compose/ui/Modifier;",
                "Lkotlin/jvm/functions/Function0;",
                "Landroidx/compose/runtime/Composer;",
                "I",
                "I",
            ).map { type -> ImmutableMethodParameter(type, emptySet(), null) }
        return ImmutableMethod(
            "Lfixture/NewPostRenderer;",
            "render",
            parameters,
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation.methodImplementation,
        )
    }

    private fun newPostRendererFixture(
        namedNullCheck: Boolean,
        directModifier: Boolean,
    ): Method {
        val implementation = MethodImplementationBuilder(14)
        if (namedNullCheck) {
            implementation.addInstruction("const-string v9, \"onClick\"".toInstruction())
            implementation.addInstruction(
                (
                    "invoke-static {v13, v9}, " +
                        "Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(" +
                        "Ljava/lang/Object;Ljava/lang/String;)V"
                ).toInstruction(),
            )
        } else {
            implementation.addInstruction(
                "invoke-virtual {v13}, Ljava/lang/Object;->getClass()Ljava/lang/Class;".toInstruction(),
            )
        }
        implementation.addInstruction(
            "invoke-interface {v2}, Lfixture/Visibility;->isVisible()Z".toInstruction(),
        )
        implementation.addInstruction("move-result v0".toInstruction())
        if (directModifier) {
            implementation.addInstruction("move-object v1, v12".toInstruction())
        } else {
            implementation.addInstruction(
                (
                    "invoke-static {v11, v12}, Lfixture/RecommendedModifiers;->create(" +
                        "Landroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)" +
                        "Landroidx/compose/ui/Modifier;"
                ).toInstruction(),
            )
            implementation.addInstruction("move-result-object v1".toInstruction())
        }
        implementation.addInstruction(
            (
                "invoke-static/range {v0 .. v8}, Landroidx/compose/animation/Fixture;->render(" +
                    "ZLandroidx/compose/ui/Modifier;Lfixture/Enter;Lfixture/Exit;" +
                    "Ljava/lang/String;Lkotlin/jvm/functions/Function3;" +
                    "Landroidx/compose/runtime/Composer;II)V"
            ).toInstruction(),
        )
        implementation.addInstruction("return-void".toInstruction())

        val parameters =
            listOf(
                "I",
                "Landroidx/compose/runtime/Composer;",
                "Landroidx/compose/ui/Modifier;",
                "Lkotlin/jvm/functions/Function0;",
            ).map { type -> ImmutableMethodParameter(type, emptySet(), null) }
        return ImmutableMethod(
            "Lfixture/NewPostRenderer;",
            "render",
            parameters,
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation.methodImplementation,
        )
    }
}
