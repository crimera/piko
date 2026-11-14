package app.crimera.tools.newx

import app.crimera.patches.newx.misc.canonicalurls.constructsProfileHeaderModel
import app.crimera.patches.newx.misc.bringbacktwitter.hasObjectAndIntParameterShape
import app.crimera.patches.newx.misc.bringbacktwitter.isLaunchRevealRenderer
import app.crimera.patches.newx.misc.bringbacktwitter.resolveLaunchRevealRenderer
import app.crimera.patches.newx.misc.dynamiccolor.isDefaultPaletteConstructorAdapter
import app.crimera.patches.newx.misc.dynamiccolor.isMediaViewerButtonContent
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstruction
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstructionOnSourcePaths
import app.crimera.patches.newx.misc.dynamiccolor.defaultPaletteConstructorRejection
import app.crimera.patches.newx.misc.postdetails.replyComposerAnchorIndices
import app.crimera.patches.newx.misc.mediatab.isInitialSubTabSeed
import app.crimera.patches.newx.misc.mediatab.isPendingSubTabStore
import app.crimera.patches.newx.models.isInlineActionKindModelConstructor
import app.crimera.patches.newx.models.ResolvedNewXInlineActionModels
import app.crimera.patches.newx.models.resolveInlineActionKindLayoutMethod
import app.crimera.patches.newx.misc.inlineactions.resolveInlineActionKindRenderCall
import app.crimera.patches.newx.misc.inlineactions.resolveInlineIconSizeHook
import app.crimera.patches.newx.misc.inlineactions.isCapturedInlineActionEntryRenderer
import app.crimera.patches.newx.misc.inlineactions.hasInlineActionEventDispatch
import app.crimera.patches.newx.misc.serverlogging.RegisterLocation
import app.crimera.patches.newx.misc.serverlogging.selectSubmitFailureOperation
import app.crimera.patches.newx.timeline.isPostDividerContentHelper
import app.crimera.patches.newx.settings.resolveSettingsRowModelTargetInMethod
import app.crimera.patches.newx.timeline.resolveInlineResumePositionFactory
import app.crimera.patches.newx.timeline.postfilter.fieldForToStringLabelThroughAppendHelper
import app.crimera.patches.newx.timeline.resumeFactoryInputs
import app.crimera.patches.newx.timeline.wideReadHighRegisters
import app.crimera.patches.newx.timeline.resolveResumePositionValidation
import app.crimera.patches.newx.premium.resolveVideoDownloadSubscriptionResults
import app.crimera.patches.newx.premium.resolveTimelineDownloadSubscriptionResults
import app.crimera.patches.newx.translation.resolveNotePostBodyTextField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderPackedSwitchPayload
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import app.crimera.patches.newx.misc.postoptions.postOptionsParameterOffset
import app.crimera.patches.newx.misc.postoptions.postOptionsFeedbackLookups
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.Opcode
import app.crimera.patches.newx.timeline.isNewPostFabHostCandidate
import app.crimera.patches.newx.timeline.primaryPostFabRendererCallIndices
import app.crimera.patches.newx.timeline.readsArrayListFromParameter
import app.crimera.patches.newx.timeline.timelineModuleDividerItemIndices
import app.crimera.patches.newx.misc.dynamiccolor.resolveTabIndicatorTintSite
import app.crimera.patches.newx.misc.replysorting.installReplySortingSelectionHook
import app.crimera.patches.newx.misc.replysorting.resolveReplySortingSelectionSite
import app.crimera.tools.lint.ResolverLinter
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
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
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewXResolverLinterTest {
    @Test
    fun `launch reveal marker fails closed while renderer matching follows its SurfaceControl work`() {
        val marker =
            ImmutableMethod(
                "Lfixture/LaunchRevealMarker;",
                "marker",
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                MethodImplementationBuilder(1).apply {
                    addInstruction("const-string v0, \"LaunchReveal\"".toInstruction())
                    addInstruction("return-void".toInstruction())
                }.methodImplementation,
            )
        fun renderer(
            parameters: List<String>,
            hasBuilder: Boolean = true,
            hasAttachedSurface: Boolean = true,
        ): Method =
            ImmutableMethod(
                "Lfixture/LaunchRevealRenderer;",
                "render",
                parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                MethodImplementationBuilder(4).apply {
                    if (hasBuilder) {
                        addInstruction("invoke-virtual {v2}, Landroid/view/SurfaceControl\$Builder;->build()Landroid/view/SurfaceControl;".toInstruction())
                    }
                    if (hasAttachedSurface) {
                        addInstruction("invoke-interface {v0, v3}, Landroid/view/AttachedSurfaceControl;->buildReparentTransaction(Landroid/view/SurfaceControl;)Landroid/view/SurfaceControl\$Transaction;".toInstruction())
                    }
                    addInstruction("return-void".toInstruction())
                }.methodImplementation,
            )
        val renderer = renderer(listOf("Landroid/view/View;", "I"))
        val swappedRenderer = renderer(listOf("I", "Landroid/view/View;"))
        val invalidRenderer = renderer(listOf("Landroid/view/View;", "Ljava/lang/String;"))
        val missingBuilderRenderer = renderer(listOf("Landroid/view/View;", "I"), hasBuilder = false)
        val missingAttachedSurfaceRenderer = renderer(listOf("Landroid/view/View;", "I"), hasAttachedSurface = false)

        assertNull(resolveLaunchRevealRenderer(emptyList(), emptyList()))
        assertFailsWith<PatchException> {
            resolveLaunchRevealRenderer(listOf(marker), emptyList())
        }
        assertTrue(isLaunchRevealRenderer(renderer))
        assertEquals(renderer, resolveLaunchRevealRenderer(listOf(marker), listOf(renderer)))
        assertTrue(isLaunchRevealRenderer(swappedRenderer))
        assertEquals(swappedRenderer, resolveLaunchRevealRenderer(listOf(marker), listOf(swappedRenderer)))
        assertFalse(isLaunchRevealRenderer(invalidRenderer))
        assertFalse(isLaunchRevealRenderer(missingBuilderRenderer))
        assertFalse(isLaunchRevealRenderer(missingAttachedSurfaceRenderer))
        assertTrue(hasObjectAndIntParameterShape(listOf("I", "Lfixture/Callback;"), "Lfixture/Callback;"))
        assertFalse(hasObjectAndIntParameterShape(listOf("I", "Lfixture/OtherCallback;"), "Lfixture/Callback;"))
    }

    @Test
    fun `branch local tint must survive every path from its load without dominating other colors`() {
        fun candidate(clobber: Boolean): Method {
            val body = MethodImplementationBuilder(4).apply {
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 3, getLabel("otherColor")))
                addInstruction("sget-wide v0, Lfixture/Colors;->active:J".toInstruction())
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 3, getLabel("shared")))
                if (clobber) addInstruction("const-wide/16 v0, 0x0".toInstruction())
                else addInstruction("nop".toInstruction())
                addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("shared")))
                addLabel("otherColor")
                addInstruction("sget-wide v0, Lfixture/Colors;->other:J".toInstruction())
                addLabel("shared")
                addInstruction("invoke-static {v0, v1}, Lfixture/Renderer;->render(J)V".toInstruction())
                addInstruction("return-void".toInstruction())
            }.methodImplementation
            return ImmutableMethod("Lfixture/Tints;", "render",
                listOf(ImmutableMethodParameter("Z", emptySet(), null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(), body)
        }
        assertFalse(candidate(false).provesValueFromInstruction(1, 0, 6, 0))
        assertTrue(candidate(false).provesValueFromInstructionOnSourcePaths(1, 0, 6, 0))
        assertFalse(candidate(true).provesValueFromInstructionOnSourcePaths(1, 0, 6, 0))
    }

    @Test
    fun `media button content accepts auxiliary parameters but requires icon and text semantics`() {
        val composer = "Landroidx/compose/runtime/Composer;"
        val modifier = "Landroidx/compose/ui/Modifier;"
        fun candidate(extraParameters: List<String>, text: Boolean = true, defaultTint: Boolean = false): Method {
            val parameters = listOf(composer, "I") + extraParameters
            val implementation = MethodImplementationBuilder(16 + parameters.size).apply {
                addInstruction("const/16 v8, ${if (defaultTint) "0x8" else "0x0"}".toInstruction())
                addInstruction(("invoke-static/range {v0 .. v8}, Lfixture/Icon;->render(" +
                    "Lcom/x/icons/b;Ljava/lang/String;${modifier}JZ${composer}II)V").toInstruction())
                if (text) addInstruction(("invoke-static {v0, v1, v2, v3}, " +
                    "Landroidx/compose/material3/Fixture;->text(Ljava/lang/String;${modifier}J)V").toInstruction())
                addInstruction("return-void".toInstruction())
            }.methodImplementation
            return ImmutableMethod("Lfixture/Button;", "render",
                parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(), implementation)
        }
        assertTrue(candidate(emptyList()).isMediaViewerButtonContent())
        assertTrue(candidate(listOf(modifier, "Lkotlin/jvm/functions/Function0;", "Ljava/lang/String;", "Z"))
            .isMediaViewerButtonContent())
        assertFalse(candidate(emptyList(), text = false).isMediaViewerButtonContent())
        assertFalse(candidate(listOf("Ljava/lang/String;"), defaultTint = true).isMediaViewerButtonContent())
    }

    @Test
    fun `mention fields follow values appended by the semantic toString formatter`() {
        val owner = "Lfixture/Mention;"
        val formatterOwner = "Lfixture/Formatter;"
        fun method(
            type: String,
            name: String,
            parameters: List<String>,
            returnType: String,
            registers: Int,
            static: Boolean = false,
            body: MethodImplementationBuilder.() -> Unit,
        ) = ImmutableMethod(
            type,
            name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
            returnType,
            AccessFlags.PUBLIC.value or (if (static) AccessFlags.STATIC.value else 0),
            emptySet(),
            emptySet(),
            MethodImplementationBuilder(registers).apply(body).methodImplementation,
        )
        fun MethodImplementationBuilder.add(instruction: String) = addInstruction(instruction.toInstruction())

        fun toStringMethod(shape: String = "direct") = method(owner, "toString", emptyList(), "Ljava/lang/String;", 6) {
            add("new-instance v0, Ljava/lang/StringBuilder;")
            add("const-string v1, \"MentionEntity(userId=\"")
            add("invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V")
            add("iget-object v1, v5, $owner->userId:Ljava/lang/Object;")
            add("invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;")
            add("const-string v1, \", startIdx=\"")
            add("invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            add("iget v1, v5, $owner->start:I")
            add("invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;")
            add("const-string v1, \", endIdx=\"")
            add("invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            add("const-string v1, \", screenName=\"")
            add("const-string v2, \")\"")
            if (shape == "alias") {
                add("iget v3, v5, $owner->end:I")
                add("move v2, v3")
                add("move v3, v2")
                add("const-string v2, \" )\"")
            } else {
                add("iget v3, v5, $owner->end:I")
            }
            if (shape == "overwritten") add("const/4 v3, 0x0")
            if (shape == "arithmeticOverwrite") add("add-int/lit8 v3, v3, 0x1")
            add("iget-object v4, v5, $owner->screenName:Ljava/lang/String;")
            // Label and value are deliberately not adjacent helper parameters.
            add("invoke-static {v3, v1, v2, v4, v0}, $formatterOwner->write(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/StringBuilder;)Ljava/lang/String;")
            add("move-result-object v4")
            add("return-object v4")
        }
        val formatter = method(
            formatterOwner,
            "write",
            listOf("I", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/StringBuilder;"),
            "Ljava/lang/String;",
            5,
            static = true,
        ) {
            add("invoke-virtual {v4, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;")
            add("invoke-virtual {v4, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            add("invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            add("invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            add("invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;")
            add("move-result-object v0")
            add("return-object v0")
        }
        val formatterClass = ImmutableClassDef(
            formatterOwner,
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet(),
            emptyList(),
            listOf(formatter),
        )

        assertEquals(
            "$owner->end:I",
            toStringMethod().fieldForToStringLabelThroughAppendHelper(", endIdx=") { type ->
                formatterClass.takeIf { it.type == type }
            }?.toString(),
        )
        assertEquals(
            "$owner->screenName:Ljava/lang/String;",
            toStringMethod().fieldForToStringLabelThroughAppendHelper(", screenName=") { type ->
                formatterClass.takeIf { it.type == type }
            }?.toString(),
        )
        assertEquals("$owner->end:I", toStringMethod("alias")
            .fieldForToStringLabelThroughAppendHelper(", endIdx=") { formatterClass }?.toString())
        assertFailsWith<PatchException> {
            toStringMethod("overwritten").fieldForToStringLabelThroughAppendHelper(", endIdx=") { formatterClass }
        }
        assertFailsWith<PatchException> {
            toStringMethod("arithmeticOverwrite").fieldForToStringLabelThroughAppendHelper(", endIdx=") { formatterClass }
        }
    }

    @Test
    fun `theme palette default constructor requires proven forwarding of customized color slots`() {
        val primary = "Lfixture/Palette;-><init>(Z${"J".repeat(17)})V"
        val valid = defaultPaletteConstructorFixture()
        assertEquals(null, valid.defaultPaletteConstructorRejection(primary))
        val misrouted = defaultPaletteConstructorFixture(misrouteToken = 13)
        assertTrue(misrouted.defaultPaletteConstructorRejection(primary).orEmpty().isNotEmpty())
        assertEquals(null, defaultPaletteConstructorFixture(shape = "redundant")
            .defaultPaletteConstructorRejection(primary))
        for (shape in listOf("bypass", "clobber", "narrowClobber")) {
            assertTrue(defaultPaletteConstructorFixture(shape = shape)
                .defaultPaletteConstructorRejection(primary) != null, shape)
        }
    }

    private fun defaultPaletteConstructorFixture(misrouteToken: Int? = null, shape: String = "direct"): ImmutableMethod {
        val palette = "Lfixture/Palette;"
        val primary = "$palette-><init>(Z${"J".repeat(17)})V"
        val implementation = MethodImplementationBuilder(72).apply {
            fun add(instruction: String) = addInstruction(instruction.toInstruction())
            add("const/high16 v0, 0x10000")
            add("and-int v0, v71, v0")
            addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, getLabel("default")))
            add("move-wide/from16 v33, v53")
            addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("forwarded")))
            addLabel("default")
            add("move-wide/from16 v33, v69")
            addLabel("forwarded")
            add("move-wide/from16 v35, v63")
            add("move-object/from16 v1, v37")
            add("move/from16 v2, v38")
            if (shape == "bypass") {
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, getLabel("delegate")))
            }
            (0..14).forEach { token ->
                val source = if (token == misrouteToken) 39 + (token - 1) * 2 else 39 + token * 2
                add("move-wide/from16 v${3 + token * 2}, v$source")
            }
            if (shape == "redundant") add("move-wide/from16 v29, v65")
            if (shape == "clobber") add("const-wide/16 v29, 0x0")
            if (shape == "narrowClobber") add("const/16 v30, 0x0")
            addLabel("delegate")
            add("invoke-direct/range {v1 .. v36}, $primary")
            add("return-void")
        }.methodImplementation
        val parameters = listOf("Z") + List(16) { "J" } + "I"
        return ImmutableMethod(
            palette,
            "<init>",
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(),
            emptySet(),
            implementation,
        )
    }

    @Test
    fun `reply content anchor follows the constructed case and requires consumed wrapper content`() {
        for (selectorType in listOf("B", "I")) {
            for (conditionalDispatch in listOf(false, true)) {
                for (shape in listOf("selected", "helperDelegation", "reorderedHelper", "otherCase", "unconsumed")) {
                    val (host, target, classes) = replyContentFixture(selectorType, conditionalDispatch, shape)
                    val description = "$selectorType conditional=$conditionalDispatch $shape"
                    if (shape == "unconsumed") {
                        val error = assertFailsWith<PatchException>(description) {
                            host.replyComposerAnchorIndices(target, classes::get)
                        }
                        assertTrue(error.message.orEmpty().contains("animated reply content consumer"), description)
                        continue
                    }
                    assertEquals(
                        if (shape in setOf("selected", "helperDelegation", "reorderedHelper")) listOf(0) else emptyList(),
                        host.replyComposerAnchorIndices(target, classes::get),
                        description,
                    )
                }
            }
        }
    }

    private fun replyContentFixture(
        selectorType: String,
        conditionalDispatch: Boolean,
        shape: String,
    ): Triple<Method, Method, Map<String, ImmutableClassDef>> {
        fun method(
            owner: String,
            name: String,
            parameters: List<String>,
            returnType: String,
            registers: Int,
            static: Boolean = false,
            body: MethodImplementationBuilder.() -> Unit,
        ) = ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returnType,
            AccessFlags.PUBLIC.value or (if (static) AccessFlags.STATIC.value else 0) or
                (if (name == "<init>") AccessFlags.CONSTRUCTOR.value else 0),
            emptySet(), emptySet(), MethodImplementationBuilder(registers).apply(body).methodImplementation,
        )
        fun MethodImplementationBuilder.add(instruction: String) = addInstruction(instruction.toInstruction())

        val content = "Lfixture/MergedReplyContent;"
        val composer = "Landroidx/compose/runtime/Composer;"
        val scalarSuffix = if (selectorType == "B") "-byte" else ""
        val target = method(
            "Lcom/x/composer/minimal/Fixture;", "container",
            listOf("Lcom/x/composer/minimal/State;", "Ldev/chrisbanes/haze/Fixture;",
                "Landroidx/compose/ui/Modifier;", "Z", "Lkotlin/jvm/functions/Function1;", composer, "I", "I"),
            "V", 8, static = true,
        ) { add("return-void") }
        val constructor = method(content, "<init>", listOf("J", selectorType), "V", 6) {
            add("move-object v0, v2")
            add("move v1, v5")
            add("iput$scalarSuffix v1, v0, $content->selector:$selectorType")
            add("invoke-direct {v0}, Ljava/lang/Object;-><init>()V")
            add("return-void")
        }
        // Resolving the class or an arbitrary constructor instead of the actual invocation chooses
        // this unrelated case and breaks the positive fixture.
        val decoyConstructor = method(content, "<init>", listOf("Ljava/lang/Object;"), "V", 2) {
            add("const/4 v1, 0x1")
            add("iput$scalarSuffix v1, v0, $content->selector:$selectorType")
            add("invoke-direct {v0}, Ljava/lang/Object;-><init>()V")
            add("return-void")
        }
        val invoke = method(content, "invoke", List(3) { "Ljava/lang/Object;" }, "Ljava/lang/Object;", 13) {
            add("move-object v1, v9")
            add("iget$scalarSuffix v0, v1, $content->selector:$selectorType")
            if (conditionalDispatch) {
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, getLabel("composer")))
            } else {
                addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, getLabel("payload")))
            }
            addLabel("unrelated")
            add("const/4 v0, 0x0")
            add("invoke-static {}, Lfixture/OtherContent;->render()V")
            // This return must terminate reachability, not fall through into another merged case.
            add("return-object v0")
            addLabel("composer")
            if (shape in setOf("helperDelegation", "reorderedHelper")) {
                if (shape == "reorderedHelper") {
                    add("const/4 v2, 0x0")
                    add("invoke-direct {v9, v11, v10, v12, v2}, $content->renderContent(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;")
                } else {
                    add("invoke-direct/range {v9 .. v12}, $content->renderContent(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                }
                add("move-result-object v1")
            } else {
                add("check-cast v10, Landroidx/compose/animation/AnimatedVisibilityScope;")
                (1..8).forEach { add("const/4 v$it, 0x0") }
                add("invoke-static/range {v1 .. v8}, $target")
            }
            add("return-object v1")
            if (!conditionalDispatch) {
                addLabel("payload")
                addInstruction(BuilderPackedSwitchPayload(0, listOf(getLabel("composer"), getLabel("unrelated"))))
            }
        }
        val host = method("Lfixture/ReplyMedia;", "render", listOf(composer), "V", 12, static = true) {
            add("new-instance v0, $content")
            add("const-wide/16 v1, 0x0")
            add("const/4 v4, ${if (shape == "otherCase") "0x1" else "0x0"}")
            add("move v3, v4")
            add("move-object v5, v0")
            add("move-wide v6, v1")
            add("move v8, v3")
            add("invoke-direct/range {v5 .. v8}, $content-><init>(J$selectorType)V")
            add("const/4 v1, 0x0")
            add("invoke-static {v1, v5, v11}, Landroidx/compose/runtime/internal/Fixture;->wrap(" +
                "ILkotlin/Function;$composer)Landroidx/compose/runtime/internal/FixtureContent;")
            add("move-result-object v0")
            add("move-object v6, v0")
            if (shape == "unconsumed") add("const/4 v6, 0x0")
            add("invoke-static {v6, v11}, Landroidx/compose/animation/Fixture;->render(" +
                "Landroidx/compose/runtime/internal/FixtureContent;$composer)V")
            add("return-void")
        }
        val helperParameters = List(3) { "Ljava/lang/Object;" } + if (shape == "reorderedHelper") listOf("I") else emptyList()
        val helper = method(content, "renderContent", helperParameters, "Ljava/lang/Object;", 12 + if (shape == "reorderedHelper") 1 else 0) {
            val scopeRegister = if (shape == "reorderedHelper") 10 else 9
            val composerRegister = if (shape == "reorderedHelper") 9 else 10
            add("check-cast v$scopeRegister, Landroidx/compose/animation/AnimatedVisibilityScope;")
            add("check-cast v$composerRegister, $composer")
            (0..4).forEach { add("const/4 v$it, 0x0") }
            add("move-object v5, v$composerRegister")
            add("const/4 v6, 0x0")
            add("const/4 v7, 0x0")
            add("invoke-static/range {v0 .. v7}, $target")
            add("const/4 v0, 0x0")
            add("return-object v0")
        }
        val definition = ImmutableClassDef(
            content, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, "Ljava/lang/Object;",
            listOf("Lkotlin/jvm/functions/Function3;"), null, emptySet(),
            listOf(ImmutableField(content, "selector", selectorType,
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, emptySet(), emptySet())),
            listOf(constructor, decoyConstructor, invoke, helper),
        )
        return Triple(host, target, mapOf(content to definition))
    }

    @Test
    fun `download capabilities require direct premium or the captured coroutine policy before mutation`() {
        val premium = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "premium", emptyList(), "Z")
        val offline = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "offline", emptyList(), "Z")
        val feature = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "feature", emptyList(), "Z")
        listOf("direct", "relocated", "unrelatedTry", "dispatchPolicy", "defaultArm").forEach { shape ->
            val (handler, classes) = downloadCapabilityFixture(shape)
            val before = handler.instructions.map { it.opcode }
            assertEquals(if (shape == "direct") 3 else 2,
                resolveVideoDownloadSubscriptionResults(handler, premium, offline, feature, classes::get).size)
            assertEquals(before, handler.instructions.map { it.opcode })
        }
        // A valid quota policy elsewhere must not stand in for the captured handler's call path.
        listOf("disconnected", "unknown", "missingResult", "wideCapture", "wrongDispatch", "selectedTry").forEach { failure ->
            val (handler, classes) = downloadCapabilityFixture(failure)
            val before = handler.instructions.map { it.opcode }
            val error = assertFailsWith<PatchException>(failure) {
                resolveVideoDownloadSubscriptionResults(handler, premium, offline, feature, classes::get)
            }
            if (failure == "selectedTry") assertTrue(error.message.orEmpty().contains("selected coroutine path overlaps exception region"))
            assertEquals(before, handler.instructions.map { it.opcode })
        }
    }

    @Test
    fun `timeline offline coverage follows both captured event arms when direct gates relocate`() {
        val premium = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "premium", emptyList(), "Z")
        val offline = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "offline", emptyList(), "Z")
        val feature = ImmutableMethodReference("Lcom/x/subscriptions/Fixture;", "feature", emptyList(), "Z")
        listOf("direct", "relocated", "secondDisconnected", "secondOtherSelector").forEach { shape ->
            val (handler, classes) = timelineDownloadCapabilityFixture(shape)
            val before = handler.instructions.map { it.opcode }
            if (shape == "direct" || shape == "relocated") {
                assertEquals(if (shape == "direct") 9 else 7,
                    resolveTimelineDownloadSubscriptionResults(handler, premium, offline, feature, classes::get).size)
            } else {
                val error = assertFailsWith<PatchException>(shape) {
                    resolveTimelineDownloadSubscriptionResults(handler, premium, offline, feature, classes::get)
                }
                // The first arm is valid in every fixture. Failure must concern the uncovered
                // sibling or its actual dispatch, not merely a missing shared marker/checker.
                assertTrue(error.message.orEmpty().contains(
                    // An absent key now selects the switch's default arm; it must still fail
                    // because that arm never clones the coroutine with the wrong selector.
                    if (shape == "secondDisconnected") "timeline offline coroutine" else "selected clone",
                ), error.message)
            }
            assertEquals(before, handler.instructions.map { it.opcode }, shape)
        }
    }

    private fun timelineDownloadCapabilityFixture(shape: String): Pair<MutableMethod, Map<String, ImmutableClassDef>> {
        // Reuse the real captured-handler -> adapter -> premium/Allowed graph, including its
        // selector-preserving create/invoke/suspend methods. Only the event callback is new.
        val (_, classes) = downloadCapabilityFixture("relocated")
        val implementation = MethodImplementationBuilder(12)
        val callback = "Lfixture/TimelineCallback;"
        val host = "Lfixture/DownloadHandler;"
        val coroutine = "Lfixture/DownloadCoroutine;"
        fun add(instruction: String) = implementation.downloadInstruction(instruction)
        add("iget-object v1, v10, $callback->host:$host")
        add("iget-object v0, v1, $host->subscriptions:Lcom/x/subscriptions/Fixture;")
        (List(6) { "feature" } + "premium").forEach { gate ->
            add("invoke-virtual {v0}, Lcom/x/subscriptions/Fixture;->$gate()Z")
            add("move-result v2")
        }
        fun saveArm(second: Boolean) {
            if (shape == "direct") {
                add("invoke-virtual {v0}, Lcom/x/subscriptions/Fixture;->offline()Z")
                add("move-result v2")
            } else {
                add("new-instance v3, $coroutine")
                add("move-object v4, v1")
                if (second && shape == "secondDisconnected") add("const/4 v4, 0x0")
                add("const/4 v5, 0x0")
                add("const/4 v6, ${if (second && shape == "secondOtherSelector") "0x6" else "0x7"}")
                add("invoke-direct {v3, v4, v5, v6}, $coroutine-><init>(Ljava/lang/Object;Lkotlin/coroutines/Continuation;B)V")
                add("invoke-static {v3}, Lkotlinx/coroutines/Fixture;->launch(Lkotlin/jvm/functions/Function2;)V")
            }
            implementation.addInstruction(BuilderInstruction10t(Opcode.GOTO, implementation.getLabel("done")))
        }
        add("instance-of v2, v11, Lfixture/SaveFirst;")
        add("const-string v8, \"download_video_to_offline\"")
        implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, implementation.getLabel("second")))
        saveArm(second = false)
        implementation.addLabel("second")
        add("instance-of v2, v11, Lfixture/SaveSecond;")
        implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, implementation.getLabel("removal")))
        saveArm(second = true)
        implementation.addLabel("removal")
        add("instance-of v2, v11, Lfixture/Remove;")
        implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, implementation.getLabel("done")))
        add("const-string v8, \"remove_video_from_offline\"")
        implementation.addLabel("done")
        add("const/4 v0, 0x0")
        add("return-object v0")
        return MutableMethod(ImmutableMethod(
            callback, "invoke", listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)),
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation.methodImplementation,
        )) to classes
    }

    private fun MethodImplementationBuilder.downloadInstruction(instruction: String) =
        addInstruction(instruction.toInstruction())

    private fun downloadCapabilityFixture(shape: String): Pair<MutableMethod, Map<String, ImmutableClassDef>> {
        fun method(owner: String, name: String, parameters: List<String>, returnType: String, registers: Int,
                   body: MethodImplementationBuilder.() -> Unit): ImmutableMethod = ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returnType,
            AccessFlags.PUBLIC.value or if (name == "<init>") AccessFlags.CONSTRUCTOR.value else 0,
            emptySet(), emptySet(), MethodImplementationBuilder(registers).apply(body).methodImplementation,
        )
        fun MethodImplementationBuilder.dispatch(register: Int, exceptionScope: String? = null, body: MethodImplementationBuilder.() -> Unit) {
            addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, register, getLabel("payload")))
            if (shape == "defaultArm") {
                // R8 folded the selected case into the switch's fall-through default arm (12.41):
                // the payload lists no key for the selector, the body follows the switch directly.
                body()
                addLabel("selected")
                downloadInstruction("return-object v0")
                addLabel("payload")
                addInstruction(BuilderPackedSwitchPayload(99, listOf(getLabel("selected"))))
                return
            }
            if (exceptionScope == "unrelated") {
                addLabel("tryStart")
                downloadInstruction("invoke-static {}, Lfixture/Unrelated;->mayThrow()V")
                addLabel("tryEnd")
            }
            downloadInstruction("return-object v0")
            addLabel("selected")
            if (exceptionScope == "selected") addLabel("tryStart")
            body()
            if (exceptionScope == "selected") addLabel("tryEnd")
            if (exceptionScope != null) {
                addLabel("catch")
                downloadInstruction("move-exception v0")
                downloadInstruction("return-object v0")
                addCatch("Ljava/lang/Exception;", getLabel("tryStart"), getLabel("tryEnd"), getLabel("catch"))
            }
            addLabel("payload")
            addInstruction(BuilderPackedSwitchPayload(7, listOf(getLabel("selected"))))
        }
        val coroutine = "Lfixture/DownloadCoroutine;"
        val handlerType = "Lfixture/DownloadHandler;"
        val quota = "Lfixture/Quota;"
        val adapter = "Lfixture/OfflineAdapter;"
        val permission = "Lfixture/Permission;"
        val constructorDescriptor = "$coroutine-><init>(Ljava/lang/Object;Lkotlin/coroutines/Continuation;B)V"
        val createDescriptor = "$coroutine->create(Ljava/lang/Object;Lkotlin/coroutines/Continuation;)Lkotlin/coroutines/Continuation;"
        val suspendDescriptor = "$coroutine->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;"
        val handler = MutableMethod(method(handlerType, "event", listOf("Ljava/lang/Object;"), "V", 12) {
            downloadInstruction("iget-object v0, v10, $handlerType->subscriptions:Lcom/x/subscriptions/Fixture;")
            val gates = listOf("feature", "premium") + if (shape == "direct") listOf("offline") else emptyList()
            gates.forEach { gate ->
                downloadInstruction("invoke-virtual {v0}, Lcom/x/subscriptions/Fixture;->$gate()Z")
                if (shape != "missingResult" || gate != "premium") downloadInstruction("move-result v2")
            }
            if (shape == "unknown") {
                downloadInstruction("invoke-virtual {v0}, Lcom/x/subscriptions/Fixture;->unrelated()Z")
                downloadInstruction("move-result v2")
            }
            downloadInstruction("const-string v0, \"download_video_to_offline\"")
            downloadInstruction("new-instance v3, $coroutine")
            downloadInstruction("move-object v4, v10")
            // v4 is the upper half of this write; a scalar reaching-origin proof must kill it.
            if (shape == "wideCapture") downloadInstruction("const-wide/16 v3, 0x0")
            downloadInstruction("const/4 v5, 0x0")
            downloadInstruction("const/4 v6, ${if (shape == "wrongDispatch") "0x6" else "0x7"}")
            downloadInstruction("invoke-direct {v3, v4, v5, v6}, $constructorDescriptor")
            downloadInstruction("invoke-static {v3}, Lkotlinx/coroutines/Fixture;->launch(Lkotlin/jvm/functions/Function2;)V")
            downloadInstruction("const-string v0, \"download_video\"")
            downloadInstruction("return-void")
        })
        val constructor = method(coroutine, "<init>", listOf("Ljava/lang/Object;", "Lkotlin/coroutines/Continuation;", "B"), "V", 4) {
            downloadInstruction("iput-byte v3, v0, $coroutine->selector:B")
            downloadInstruction("iput-object v1, v0, $coroutine->handler:Ljava/lang/Object;")
            downloadInstruction("const/4 v1, 0x2")
            downloadInstruction("invoke-direct {v0, v1, v2}, Lkotlin/coroutines/jvm/internal/SuspendLambda;-><init>(ILkotlin/coroutines/Continuation;)V")
            downloadInstruction("return-void")
        }
        val create = method(coroutine, "create", listOf("Ljava/lang/Object;", "Lkotlin/coroutines/Continuation;"), "Lkotlin/coroutines/Continuation;", 8) {
            downloadInstruction("iget-byte v0, v5, $coroutine->selector:B")
            dispatch(0) {
                downloadInstruction("new-instance v0, $coroutine")
                downloadInstruction("iget-object v1, v5, $coroutine->handler:Ljava/lang/Object;")
                downloadInstruction("move-object v2, v7")
                downloadInstruction("const/4 v3, 0x7")
                downloadInstruction("invoke-direct {v0, v1, v2, v3}, $constructorDescriptor")
                downloadInstruction("return-object v0")
            }
        }
        val invoke = method(coroutine, "invoke", listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), "Ljava/lang/Object;", 5) {
            downloadInstruction("iget-byte v0, v2, $coroutine->selector:B")
            dispatch(0) {
                downloadInstruction("invoke-virtual {v2, v3, v4}, $createDescriptor")
                downloadInstruction("move-result-object v0")
                downloadInstruction("invoke-virtual {v0, v3}, $suspendDescriptor")
                downloadInstruction("move-result-object v0")
                downloadInstruction("return-object v0")
            }
        }
        val suspendMethod = method(
            coroutine,
            "invokeSuspend",
            listOf("Ljava/lang/Object;"),
            "Ljava/lang/Object;",
            if (shape == "dispatchPolicy") 5 else 3,
        ) {
            downloadInstruction(
                "iget-byte v0, ${if (shape == "dispatchPolicy") "v3" else "v1"}, $coroutine->selector:B",
            )
            val exceptionScope = when (shape) {
                "unrelatedTry" -> "unrelated"
                "selectedTry" -> "selected"
                else -> null
            }
            dispatch(0, exceptionScope) {
                if (shape == "dispatchPolicy") {
                    downloadInstruction("iget-object v0, v3, $coroutine->handler:Ljava/lang/Object;")
                    downloadInstruction("check-cast v0, $handlerType")
                    downloadInstruction("iget-object v1, v0, $handlerType->policy:$adapter")
                    downloadInstruction("const-string v2, \"post-key\"")
                    downloadInstruction("invoke-virtual {v1, v2, v3}, $adapter->policy(Ljava/lang/String;Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Enum;")
                    downloadInstruction("move-result-object v0")
                } else {
                    downloadInstruction("invoke-direct {v1, v2}, $coroutine->step(Ljava/lang/Object;)Ljava/lang/Object;")
                    downloadInstruction("move-result-object v0")
                }
                downloadInstruction("return-object v0")
            }
        }
        val step = method(coroutine, "step", listOf("Ljava/lang/Object;"), "Ljava/lang/Object;", 6) {
            downloadInstruction("iget-object v0, v4, $coroutine->handler:Ljava/lang/Object;")
            downloadInstruction("check-cast v0, $handlerType")
            downloadInstruction("iget-object v1, v0, $handlerType->policy:$adapter")
            downloadInstruction("const-string v2, \"post-key\"")
            downloadInstruction("invoke-virtual {v1, v2, v4}, $adapter->policy(Ljava/lang/String;Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Enum;")
            downloadInstruction("move-result-object v0")
            downloadInstruction("return-object v0")
        }
        val policy = method(adapter, "policy", listOf("Ljava/lang/String;", "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"), "Ljava/lang/Enum;", 5) {
            if (shape != "disconnected") {
                downloadInstruction("iget-object v0, v2, $adapter->quota:$quota")
                downloadInstruction("const/4 v1, 0x0")
                downloadInstruction("invoke-virtual {v0, v1}, $quota->check(I)$permission")
                downloadInstruction("move-result-object v0")
            } else downloadInstruction("const/4 v0, 0x0")
            downloadInstruction("return-object v0")
        }
        val check = method(quota, "check", listOf("I"), permission, 4) {
            downloadInstruction("iget-object v0, v2, $quota->subscriptions:Lcom/x/subscriptions/Fixture;")
            downloadInstruction("invoke-virtual {v0}, Lcom/x/subscriptions/Fixture;->offline()Z")
            downloadInstruction("move-result v0")
            addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, getLabel("free")))
            downloadInstruction("sget-object v0, $permission->Allowed:$permission")
            downloadInstruction("return-object v0")
            addLabel("free")
            downloadInstruction("const-string v0, \"android_x_lite_offline_free_saves_enabled\"")
            downloadInstruction("const-string v1, \"android_x_lite_offline_free_saves_limit\"")
            downloadInstruction("sget-object v0, $permission->PremiumOnly:$permission")
            downloadInstruction("return-object v0")
        }
        fun owner(type: String, superclass: String, methods: List<Method>) = ImmutableClassDef(
            type, AccessFlags.PUBLIC.value, superclass, emptyList(), null, emptySet(), emptyList(), methods,
        )
        val classes = listOf(
            owner(coroutine, "Lkotlin/coroutines/jvm/internal/SuspendLambda;", listOf(constructor, create, invoke, suspendMethod, step)),
            owner(adapter, "Ljava/lang/Object;", listOf(policy)), owner(quota, "Ljava/lang/Object;", listOf(check)),
        ).associateBy { it.type }
        return handler to classes
    }

    @Test
    fun `inline sizing resolver marks the native getter result and rejects invalid producers`() {
        val (renderer, classes) = inlineSizingFixture()
        val consumer = renderer.instructions.indexOfFirst { it.opcode == Opcode.INVOKE_STATIC }
        // Real no-F renderer path: the native sizing getter produces the float passed downstream.
        assertEquals(consumer to 7, resolveInlineIconSizeHook(renderer, classes::get))
        val (legacy, legacyClasses) = inlineSizingFixture("directFloat")
        assertEquals(0 to 15, resolveInlineIconSizeHook(legacy, legacyClasses::get))
        listOf("getterOverwrite", "entryOverwrite", "sizingOverwrite", "missingResult", "duplicateCall").forEach { failure ->
            val (source, fixtureClasses) = inlineSizingFixture(failure)
            assertFailsWith<PatchException>(failure) {
                resolveInlineIconSizeHook(source, fixtureClasses::get)
            }
        }
    }

    private fun inlineSizingFixture(failure: String? = null): Pair<MutableMethod, Map<String, ImmutableClassDef>> {
        val sizing = "Lfixture/ActionSizing;"
        val auxiliary = "Lfixture/AuxiliarySizing;"
        fun method(owner: String, name: String, parameters: List<String>, result: String, registers: Int,
                   static: Boolean = false, body: MethodImplementationBuilder.() -> Unit) = ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, result,
            AccessFlags.PUBLIC.value or (if (static) AccessFlags.STATIC.value else 0), emptySet(), emptySet(),
            MethodImplementationBuilder(registers).apply(body).methodImplementation,
        )
        fun MethodImplementationBuilder.add(instruction: String) = addInstruction(instruction.toInstruction())
        fun sizingClass(owner: String, semantic: Boolean): ImmutableClassDef {
            val getter = method(owner, "read", emptyList(), "F", 1) {
                if (semantic && failure == "getterOverwrite") add("const/4 v0, 0x0")
                add("iget v0, v0, $owner->size:F")
                add("return v0")
            }
            val description = method(owner, "toString", emptyList(), "Ljava/lang/String;", 2) {
                if (semantic) {
                    add("const-string v0, \"InlineActionSizing(counterFontSize=\"")
                    add("const-string v0, \", iconSize=\"")
                } else {
                    add("const-string v0, \"UnrelatedSizing(size=\"")
                }
                add("return-object v0")
            }
            return ImmutableClassDef(
                owner, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null, emptySet(),
                listOf(ImmutableField(owner, "size", "F", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())),
                listOf(getter, description),
            )
        }
        val renderer = method(
            "Lfixture/EntryRenderer;", "render",
            listOf("Lfixture/Entry;", "J", auxiliary, if (failure == "directFloat") "F" else sizing),
            "V", 16, static = true,
        ) {
            add("move-object v1, v11")
            if (failure == "entryOverwrite") add("const/16 v11, 0x0")
            if (failure == "sizingOverwrite") add("const/16 v15, 0x0")
            if (failure != "directFloat") {
                add("invoke-virtual/range {v15 .. v15}, $sizing->read()F")
                add(if (failure == "missingResult") "nop" else "move-result v7")
                if (failure == "duplicateCall") {
                    add("invoke-virtual {v15}, $sizing->read()F")
                    add("move-result v7")
                }
            }
            val nativeSize = if (failure == "directFloat") 15 else 7
            add("invoke-static {v$nativeSize}, Lfixture/NativeIcon;->render(F)V")
            add("return-void")
        }
        return MutableMethod(renderer) to mapOf(sizing to sizingClass(sizing, true), auxiliary to sizingClass(auxiliary, false))
    }

    @Test
    fun `inline event callback dispatches the constructed event not another captured object`() {
        val event = "Lfixture/LongClick;"
        val constructor = ImmutableMethodReference(event, "<init>", listOf("Lfixture/Entry;"), "V")
        fun callback(range: Boolean, failure: String? = null): Method {
            val implementation = MethodImplementationBuilder(6)
            fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
            add("new-instance v1, ${if (failure == "wrongAllocation") "Lfixture/PressState;" else event}")
            add(if (range) "invoke-direct/range {v1 .. v2}, $constructor" else "invoke-direct {v1, v2}, $constructor")
            if (failure == "overwrite") add("move-object v1, v3")
            val invoke = "Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;"
            add(when {
                failure == "wrongArgument" -> "invoke-interface {v0, v3}, $invoke"
                range -> "invoke-interface/range {v0 .. v1}, $invoke"
                else -> "invoke-interface {v0, v1}, $invoke"
            })
            add("return-object v0")
            return ImmutableMethod(
                "Lfixture/RepackagedCallback;", "invoke",
                listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)),
                "Ljava/lang/Object;", AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation.methodImplementation,
            )
        }
        listOf(false, true).forEach { range ->
            assertTrue(hasInlineActionEventDispatch(callback(range), constructor))
            listOf("wrongAllocation", "wrongArgument", "overwrite").forEach { failure ->
                assertFalse(hasInlineActionEventDispatch(callback(range, failure), constructor), failure)
            }
        }
    }

    @Test
    fun `inline layout discovery and kind hook follow receiver churn and reject unproven staging`() {
        val field = ImmutableFieldReference("Lfixture/Entry;", "action", "Lfixture/Action;")
        val decoy = inlineKindLayoutFixture(3, unrelatedField = true)
        listOf(2, 3).forEach { arity ->
            val source = inlineKindLayoutFixture(arity)
            val resolved = resolveInlineActionKindLayoutMethod(listOf(decoy, source), field)
            val instructions = resolved.instructions.toList()
            val constructor = instructions.indices.single { index ->
                isInlineActionKindModelConstructor(instructions[index]) { it == "Lfixture/Kind;" }
            }
            // Exercises the same semantic selection and render proof used before production emission.
            assertEquals(instructions.lastIndex - 1, resolveInlineActionKindRenderCall(resolved, constructor, 2, "Lfixture/Entry;"))
        }
        assertFailsWith<PatchException> { resolveInlineActionKindLayoutMethod(listOf(decoy), field) }
        assertFailsWith<PatchException> {
            resolveInlineActionKindLayoutMethod(listOf(inlineKindLayoutFixture(2), inlineKindLayoutFixture(3)), field)
        }
        listOf("overwrite", "wideOverwrite", "incoming", "outgoing").forEach { failure ->
            val source = resolveInlineActionKindLayoutMethod(listOf(inlineKindLayoutFixture(3, failure)), field)
            val instructions = source.instructions.toList()
            val constructor = instructions.indices.single { index ->
                isInlineActionKindModelConstructor(instructions[index]) { it == "Lfixture/Kind;" }
            }
            assertFailsWith<PatchException>(failure) {
                resolveInlineActionKindRenderCall(source, constructor, 2, "Lfixture/Entry;")
            }
        }
    }

    private fun inlineKindLayoutFixture(
        arity: Int,
        failure: String? = null,
        unrelatedField: Boolean = false,
    ): Method {
        val implementation = MethodImplementationBuilder(24)
        fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
        add("check-cast v2, Lfixture/Entry;")
        val fieldName = if (unrelatedField) "unrelated" else "action"
        add("iget-object v9, v2, Lfixture/Entry;->$fieldName:Lfixture/Action;")
        if (failure == "incoming") {
            implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 9, implementation.getLabel("render")))
        }
        add("new-instance v14, Lfixture/ParentData;")
        add("invoke-direct {v14, v11, v4, v12}, Lfixture/ParentData;-><init>(ZZLfixture/Kind;)V")
        if (arity == 3) {
            // Actual 12.37 staging: preserve the old v1, copy entry, then overwrite entry's register.
            add("move-object v12, v1")
            add("move-object v1, v2")
            add("move-object v2, v13")
        }
        if (failure == "outgoing") {
            implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 9, implementation.getLabel("exit")))
        }
        if (failure == "overwrite") add("const/4 v1, 0x0")
        if (failure == "wideOverwrite") add("const-wide/16 v0, 0x0")
        implementation.addLabel("render")
        val renderRegister = if (arity == 3) 1 else 2
        add("invoke-static {v$renderRegister}, Lfixture/Renderer;->render(Lfixture/Entry;)V")
        implementation.addLabel("exit")
        add("return-object v0")
        return ImmutableMethod(
            "Lcom/x/inlineactionbar/Fixture;", "invoke",
            List(arity) { ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null) },
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
            implementation.methodImplementation,
        )
    }

    @Test
    fun `post options feedback lookup accepts virtual concrete maps without confusing receiver or result`() {
        listOf("Ljava/util/Map;", "Ljava/util/LinkedHashMap;").forEach { mapType ->
            val field = ImmutableFieldReference("Lfixture/State;", "feedback", mapType)
            listOf("valid", "wrongReceiver", "missingResult").forEach { shape ->
                val code = MethodImplementationBuilder(6)
                code.addInstruction("iget-object v0, v4, Lfixture/State;->feedback:$mapType".toInstruction())
                val invoke = if (mapType == "Ljava/util/Map;") "invoke-interface" else "invoke-virtual"
                val receiver = if (shape == "wrongReceiver") 3 else 0
                code.addInstruction("$invoke {v$receiver, v5}, $mapType->get(Ljava/lang/Object;)Ljava/lang/Object;".toInstruction())
                code.addInstruction((if (shape == "missingResult") "const/4 v2, 0x0" else "move-result-object v2").toInstruction())
                code.addInstruction("return-object v2".toInstruction())
                val method = ImmutableMethod("Lfixture/Renderer;", "render",
                    listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)), "Ljava/lang/Object;",
                    AccessFlags.PUBLIC.value, emptySet(), emptySet(), code.methodImplementation)
                val lookups = postOptionsFeedbackLookups(method, field) { it in setOf("Ljava/util/Map;", "Ljava/util/LinkedHashMap;") }
                assertEquals(if (shape == "valid") listOf(5 to 2) else emptyList(),
                    lookups.map { it.actionRegister to it.labelRegister })
            }
        }
    }

    @Test
    fun `post options constructor follows the semantic field input across auxiliary type and parameter churn`() {
        val optionsField = ImmutableFieldReference("Lfixture/State;", "options", "Ljava/util/List;")
        listOf("Ljava/util/Map;", "Ljava/util/LinkedHashMap;").forEach { auxiliaryType ->
            assertEquals(4, postOptionsParameterOffset(postOptionsConstructorFixture(auxiliaryType), optionsField))
        }
        assertEquals(4, postOptionsParameterOffset(
            postOptionsConstructorFixture("Ljava/util/LinkedHashMap;", filterOptions = true), optionsField,
        ))
        listOf(
            postOptionsConstructorFixture("Ljava/util/LinkedHashMap;", overwriteOptions = true),
            postOptionsConstructorFixture("Ljava/util/Map;", conditionalStore = true),
            postOptionsConstructorFixture("Ljava/util/Map;", filterOptions = true, unrelatedFilter = true),
        ).forEach { constructor ->
            assertFailsWith<PatchException> { postOptionsParameterOffset(constructor, optionsField) }
        }
    }

    private fun postOptionsConstructorFixture(
        auxiliaryType: String,
        overwriteOptions: Boolean = false,
        conditionalStore: Boolean = false,
        filterOptions: Boolean = false,
        unrelatedFilter: Boolean = false,
    ): Method {
        val implementation = MethodImplementationBuilder(16)
        fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
        add("invoke-direct {v10}, Ljava/lang/Object;-><init>()V")
        add("move-object v0, v10")
        add("move-object v1, v14")
        // A second List field and parameter must not select the wrong list input.
        add("iput-object v15, v0, Lfixture/State;->unrelated:Ljava/util/List;")
        if (conditionalStore) {
            implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 13, implementation.getLabel("store")))
        }
        if (overwriteOptions) add("const/4 v1, 0x0")
        if (filterOptions) {
            val filterOwner = if (unrelatedFilter) "Lfixture/Unrelated;" else
                app.crimera.patches.newx.utils.Constants.POST_OPTIONS_FILTER_DESCRIPTOR
            add("invoke-static {v1, v2}, $filterOwner->filter(Ljava/util/List;Ljava/util/Set;)Ljava/util/List;")
            add("move-result-object v1")
        }
        implementation.addLabel("store")
        add("iput-object v1, v0, Lfixture/State;->options:Ljava/util/List;")
        add("return-void")
        return ImmutableMethod(
            "Lfixture/State;", "<init>",
            listOf("J", auxiliaryType, "Ljava/util/List;", "Ljava/util/List;")
                .map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
            emptySet(), emptySet(), implementation.methodImplementation,
        )
    }

    @Test
    fun `settings model discovery follows the title through list builders and ignores unrelated rows`() {
        val titleClass = ImmutableClassDef(
            "Lfixture/ResourceText;", AccessFlags.PUBLIC.value, "Lfixture/Text;", emptyList(), null, emptySet(),
            listOf(ImmutableField("Lfixture/ResourceText;", "resource", "I", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())),
            emptyList(),
        )
        listOf("Ljava/lang/Object;", "Ljava/util/List;").forEach { returnType ->
            val target = resolveSettingsRowModelTargetInMethod(settingsModelBuilderFixture(returnType), 1) { titleClass }
            assertEquals("Lfixture/Row;", target?.method?.definingClass)
            assertEquals(5, target?.clickParameterIndex)
            assertEquals(8, target?.maskParameterIndex)
        }
        assertEquals(null, resolveSettingsRowModelTargetInMethod(settingsModelBuilderFixture("Ljava/util/List;", overwriteTitle = true), 1) { titleClass })
    }

    private fun settingsModelBuilderFixture(returnType: String, overwriteTitle: Boolean = false): Method {
        val implementation = MethodImplementationBuilder(24)
        fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
        add("new-instance v0, Lfixture/ResourceText;")
        add("const v1, 0x12345678")
        repeat(5) { add("nop") }
        add("invoke-direct {v0, v1}, Lfixture/ResourceText;-><init>(I)V")
        add("move-object/from16 v11, v0")
        if (overwriteTitle) add("const/16 v11, 0x0")
        val parameters = "Lfixture/Text;Lfixture/Text;Lcom/x/icons/Fixture;Lfixture/Auxiliary;ZLkotlin/jvm/functions/Function0;Lfixture/Destination;Lfixture/Menu;I"
        add("new-instance v10, Lfixture/Row;")
        add("invoke-direct/range {v10 .. v19}, Lfixture/Row;-><init>($parameters)V")
        // An unrelated row after the anchor must not become another constructor candidate.
        add("const/16 v11, 0x0")
        add("new-instance v10, Lfixture/UnrelatedRow;")
        add("invoke-direct/range {v10 .. v19}, Lfixture/UnrelatedRow;-><init>($parameters)V")
        add("const/4 v0, 0x0")
        add("return-object v0")
        return ImmutableMethod(
            "Lfixture/RepackagedBuilder;", "build", emptyList(), returnType, AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), implementation.methodImplementation,
        )
    }

    @Test
    fun `resume validator follows the list index and matcher when the helper is inlined`() {
        listOf(false, true).forEach { inlined ->
            val (source, classes) = resumeValidatorFixture(inlined)
            val validation = resolveResumePositionValidation(source, "Lfixture/Resume;", classes::get)!!
            assertEquals("itemIndex", validation.itemIndexField.name)
            assertEquals("matches", validation.matcher.name)
        }
        listOf("index", "item").forEach { overwrite ->
            val (source, classes) = resumeValidatorFixture(true, overwrite)
            assertFailsWith<PatchException> {
                resolveResumePositionValidation(source, "Lfixture/Resume;", classes::get)
            }
        }
    }

    private fun resumeValidatorFixture(
        inlined: Boolean,
        overwrite: String? = null,
    ): Pair<Method, Map<String, ImmutableClassDef>> {
        fun validatorBody(implementation: MethodImplementationBuilder, listRegister: Int, returnObject: Boolean) {
            fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
            add("iget v0, v6, Lfixture/Resume;->rowIndex:I")
            add("iget v0, v6, Lfixture/Resume;->itemIndex:I")
            if (overwrite == "index") add("const/4 v0, 0x0")
            add("invoke-static {v$listRegister, v0}, Lkotlin/collections/CollectionsKt;->getOrNull(Ljava/util/List;I)Ljava/lang/Object;")
            add("move-result-object v2")
            add("check-cast v2, Lfixture/Item;")
            if (overwrite == "item") add("const/4 v2, 0x0")
            add("iget-object v3, v6, Lfixture/Resume;->entryId:Ljava/lang/String;")
            add("iget-wide v4, v6, Lfixture/Resume;->sortIndex:J")
            add("invoke-static {v2, v3, v4, v5}, Lfixture/Matcher;->matches(Lfixture/Item;Ljava/lang/String;J)Z")
            add("move-result v0")
            if (returnObject) add("const/4 v0, 0x0")
            add(if (returnObject) "return-object v0" else "return v0")
        }
        val helperCode = MethodImplementationBuilder(8)
        validatorBody(helperCode, 7, false)
        val helper = ImmutableMethod(
            "Lfixture/Validator;", "validate",
            listOf("Lfixture/Resume;", "Ljava/util/List;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "Z", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(), helperCode.methodImplementation,
        )
        val sourceCode = MethodImplementationBuilder(8)
        sourceCode.addInstruction("iget-object v6, v7, Lfixture/Owner;->resume:Lfixture/Resume;".toInstruction())
        sourceCode.addInstruction("iget-object v1, v7, Lfixture/Owner;->items:Ljava/util/List;".toInstruction())
        if (inlined) validatorBody(sourceCode, 1, true) else {
            sourceCode.addInstruction("invoke-static {v6, v1}, Lfixture/Validator;->validate(Lfixture/Resume;Ljava/util/List;)Z".toInstruction())
            sourceCode.addInstruction("move-result v0".toInstruction())
            sourceCode.addInstruction("const/4 v0, 0x0".toInstruction())
            sourceCode.addInstruction("return-object v0".toInstruction())
        }
        val source = ImmutableMethod(
            "Lfixture/Owner;", "postLoad", emptyList(), "Lfixture/Holder;", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), sourceCode.methodImplementation,
        )
        val helperClass = ImmutableClassDef(
            "Lfixture/Validator;", AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptySet(), emptyList(), listOf(helper),
        )
        return source to mapOf(helperClass.type to helperClass)
    }

    @Test
    fun `inline resume factory preserves self-read inputs and rejects escaping branches`() {
        val wideInvokeInput = MethodImplementationBuilder(3).apply {
            addInstruction("invoke-static {v0, v1}, Lfixture/Time;->consume(J)V".toInstruction())
            addInstruction("const/4 v2, 0x0".toInstruction())
            addInstruction("return-object v2".toInstruction())
        }.methodImplementation.instructions.toList()
        assertEquals(setOf(1), wideInvokeInput.first().wideReadHighRegisters())
        assertEquals(setOf(0), resumeFactoryInputs(wideInvokeInput))

        fun extract(source: Method) = resolveInlineResumePositionFactory(
            source,
            listOf("Ljava/util/List;", "Ljava/util/ArrayList;", "Lfixture/Holder;", "Lkotlin/time/Instant;"),
            "Lfixture/Resume;",
            ImmutableFieldReference("Lfixture/Owner;", "items", "Ljava/util/List;"),
            2,
        ).method
        val extracted = extract(inlineResumeFactoryFixture(escape = false))
        val nonzeroSeed = extract(inlineResumeFactoryFixture(escape = false, seed = 1))
        assertTrue(nonzeroSeed.instructions.any {
            it.opcode == Opcode.CONST_4 &&
                (it as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction)?.registerA == 3 &&
                (it as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)?.narrowLiteral == 1
        })
        val wideOverlapSpill = extract(inlineResumeFactoryFixture(escape = false, wideOverlapSpill = true))
        assertTrue(wideOverlapSpill.instructions.any { it.opcode == Opcode.NOP })
        val constantWideOverlap = extract(
            inlineResumeFactoryFixture(escape = false, constantWideOverlapInput = true),
        )
        assertTrue(constantWideOverlap.instructions.any {
            it.opcode == Opcode.MOVE_OBJECT && it.registersUsed == listOf(5, 14)
        })
        assertTrue(constantWideOverlap.instructions.any {
            it.opcode in setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST) &&
                (it as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction)?.registerA == 14 &&
                (it as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)?.narrowLiteral == 0
        })
        val code = extracted.instructions.toList()
        assertEquals(Opcode.RETURN_OBJECT, code.last().opcode)
        assertTrue(code.any { it.opcode == Opcode.INVOKE_DIRECT_RANGE })
        // Repeated register operands must not erase an input: iget v2,v2 consumes the holder.
        assertEquals(Opcode.MOVE_OBJECT, code[2].opcode)
        val offsets = code.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
        val branchIndex = code.indexOfFirst { it.opcode == Opcode.IF_EQZ }
        val branch = code[branchIndex] as com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
        assertEquals(offsets[code.lastIndex], offsets[branchIndex] + branch.codeOffset)
        listOf(
            inlineResumeFactoryFixture(escape = true),
            inlineResumeFactoryFixture(escape = false, bypassSeed = true),
        ).forEach { source ->
            assertFailsWith<PatchException> {
                resolveInlineResumePositionFactory(
                    source,
                    listOf("Ljava/util/List;", "Ljava/util/ArrayList;", "Lfixture/Holder;", "Lkotlin/time/Instant;"),
                    "Lfixture/Resume;",
                    ImmutableFieldReference("Lfixture/Owner;", "items", "Ljava/util/List;"),
                    2,
                )
            }
        }
    }

    private fun inlineResumeFactoryFixture(
        escape: Boolean,
        seed: Int = 0,
        bypassSeed: Boolean = false,
        wideOverlapSpill: Boolean = false,
        constantWideOverlapInput: Boolean = false,
    ): Method {
        val implementation = MethodImplementationBuilder(15)
        fun add(instruction: String) = implementation.addInstruction(instruction.toInstruction())
        add("iget-object v0, v14, Lfixture/Owner;->items:Ljava/util/List;")
        add("iget-object v1, v14, Lfixture/Owner;->rows:Ljava/util/ArrayList;")
        add("iget-object v2, v14, Lfixture/Owner;->holder:Lfixture/Holder;")
        add("const/4 v3, 0x1")
        if (bypassSeed) implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 14, implementation.getLabel("seeded")))
        add("const/4 v3, $seed")
        implementation.addLabel("seeded")
        add("const/4 v4, 0x0")
        add("iget-object v5, v14, Lfixture/Owner;->clock:Lfixture/Clock;")
        if (constantWideOverlapInput) add("const/4 v14, 0x0")
        add("invoke-interface {v5}, Lfixture/Clock;->now()Lkotlin/time/Instant;")
        add("move-result-object v12")
        if (constantWideOverlapInput) {
            add("move-object v5, v14")
            add("const-wide/16 v13, 0x0")
        }
        if (wideOverlapSpill) {
            add("move-object/from16 v5, v7")
            add("const-wide/16 v6, 0x0")
            add("move-object/from16 v7, v5")
        }
        add("move/from16 v11, v3")
        add("move v11, v3")
        implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 3, implementation.getLabel(if (escape) "outside" else "end")))
        add("iget v2, v2, Lfixture/Holder;->index:I")
        add("invoke-interface {v0}, Ljava/util/List;->size()I")
        add("invoke-virtual {v1}, Ljava/util/ArrayList;->size()I")
        add("const-string v6, \"entry\"")
        add("const-wide/16 v7, 0x0")
        add("const/4 v9, 0x0")
        add("const/4 v10, 0x1")
        add("const/4 v11, 0x0")
        add("new-instance v5, Lfixture/Resume;")
        add("invoke-direct/range {v5 .. v12}, Lfixture/Resume;-><init>(Ljava/lang/String;JIIILkotlin/time/Instant;)V")
        add("move-object v4, v5")
        implementation.addLabel("end")
        add("nop")
        implementation.addLabel("outside")
        add("return-void")
        return ImmutableMethod(
            "Lfixture/Owner;", "save",
            listOf("Lfixture/Holder;", "Lfixture/Owner;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), implementation.methodImplementation,
        )
    }

    @Test
    fun `post divider discovery survives merged callers and rejects unguarded drawing`() {
        // Both 12.35 prod and 12.36 beta have this same helper; only their synthetic callers moved.
        assertTrue(dividerContentFixture(guarded = true).isPostDividerContentHelper())
        assertFalse(dividerContentFixture(guarded = false).isPostDividerContentHelper())
        assertFalse(dividerContentFixture(guarded = true, overwriteVisibility = true).isPostDividerContentHelper())
    }

    private fun dividerContentFixture(guarded: Boolean, overwriteVisibility: Boolean = false): Method {
        val implementation = MethodImplementationBuilder(15)
        implementation.addInstruction(
            "invoke-virtual {v12, v11, v0}, Landroidx/compose/runtime/internal/Fixture;->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".toInstruction(),
        )
        if (overwriteVisibility) implementation.addInstruction("const/4 v14, 0x0".toInstruction())
        if (guarded) {
            implementation.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 14, implementation.getLabel("afterDivider")))
        }
        implementation.addInstruction(
            "invoke-static/range {v0 .. v6}, Landroidx/compose/material3/Fixture;->draw(FIIJLandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;)V".toInstruction(),
        )
        implementation.addLabel("afterDivider")
        implementation.addInstruction("return-void".toInstruction())
        return ImmutableMethod(
            "Lfixture/MovedContentDivider;", "render",
            listOf("I", "I", "Landroidx/compose/runtime/Composer;", "Landroidx/compose/runtime/internal/Fixture;", "Landroidx/compose/ui/Modifier;", "Z")
                .map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet<ImmutableAnnotation>(), emptySet<HiddenApiRestriction>(), implementation.methodImplementation,
        )
    }

    @Test
    fun `captured inline-action resolver follows the captured entry through field reads`() {
        val entryType = "Lfixture/InlineEntry;"
        val actionType = "Lfixture/ActionType;"
        val models = ResolvedNewXInlineActionModels(
            entryType,
            ImmutableFieldReference(entryType, "actionType", actionType),
            ImmutableFieldReference(entryType, "enabled", "Z"),
            actionType,
        )
        val (owner, renderer) = capturedInlineActionRendererFixture(entryBaseRegister = 4, extraGuardTypeRead = true)
        val (decoyOwner, decoyRenderer) = capturedInlineActionRendererFixture(entryBaseRegister = 8, extraGuardTypeRead = true)

        assertTrue(renderer.isCapturedInlineActionEntryRenderer(owner, models))
        assertFalse(decoyRenderer.isCapturedInlineActionEntryRenderer(decoyOwner, models))
    }

    @Test
    fun `post FAB host is found by its menu and renderer contract`() {
        val host = postFabHostFixture(includeMenuContract = true)
        val lookalike = postFabHostFixture(includeMenuContract = false)

        assertEquals(listOf(0), host.primaryPostFabRendererCallIndices())
        assertTrue(host.isNewPostFabHostCandidate())
        assertFalse(lookalike.isNewPostFabHostCandidate())
    }

    private fun capturedInlineActionRendererFixture(
        entryBaseRegister: Int,
        extraGuardTypeRead: Boolean = false,
    ): Pair<ImmutableClassDef, Method> {
        val ownerType = "Lfixture/CapturedActionRenderer;"
        val entryType = "Lfixture/InlineEntry;"
        val actionType = "Lfixture/ActionType;"
        val implementation = MethodImplementationBuilder(10).apply {
            addInstruction("iget-object v5, v$entryBaseRegister, $ownerType->entry:$entryType".toInstruction())
            if (extraGuardTypeRead) {
                addInstruction("iget-object v8, v5, $entryType->actionType:$actionType".toInstruction())
                addInstruction("sget-object v9, $actionType->twitterShare:$actionType".toInstruction())
                addInstruction(BuilderInstruction22t(Opcode.IF_NE, 8, 9, getLabel("guarded")))
                addInstruction("nop".toInstruction())
                addLabel("guarded")
            }
            addInstruction("iget-object v6, v5, $entryType->actionType:$actionType".toInstruction())
            addInstruction("iget-boolean v7, v5, $entryType->enabled:Z".toInstruction())
            addInstruction("invoke-static {v6}, Lfixture/ActionRenderer;->render($actionType)V".toInstruction())
            addInstruction("const/4 v0, 0x0".toInstruction())
            addInstruction("return-object v0".toInstruction())
        }.methodImplementation
        val renderer = ImmutableMethod(
            ownerType,
            "invoke",
            List(5) { ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null) },
            "Ljava/lang/Object;",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            implementation,
        )
        val owner = ImmutableClassDef(
            ownerType,
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            listOf("Lkotlin/jvm/functions/Function5;"),
            null,
            emptySet(),
            listOf(ImmutableField(ownerType, "entry", entryType, AccessFlags.PRIVATE.value, null, emptySet(), emptySet())),
            listOf(renderer),
        )
        return owner to renderer
    }

    private fun postFabHostFixture(includeMenuContract: Boolean): Method {
        val rendererParameters = if (includeMenuContract) {
            listOf(
                "Ljava/lang/String;",
                "Landroidx/compose/ui/Modifier;",
                "Landroidx/compose/runtime/Composer;",
                "J",
                "J",
                "Lkotlin/jvm/functions/Function0;",
                "Lkotlin/jvm/functions/Function0;",
            )
        } else {
            listOf(
                "Ljava/lang/String;",
                "Landroidx/compose/ui/Modifier;",
                "Landroidx/compose/runtime/Composer;",
                "J",
                "Lkotlin/jvm/functions/Function0;",
            )
        }
        val implementation = MethodImplementationBuilder(16).apply {
            val reference = "Lfixture/ui/Renderer;->draw(${rendererParameters.joinToString("")})V"
            val lastRegister = rendererParameters.sumOf { type -> if (type == "J" || type == "D") 2 else 1 } - 1
            addInstruction("invoke-static/range {v0 .. v$lastRegister}, $reference".toInstruction())
            addInstruction("return-void".toInstruction())
        }.methodImplementation
        return ImmutableMethod(
            "Lfixture/ui/ComposeHost;",
            "changedName",
            listOf(
                "I",
                "Landroidx/compose/runtime/Composer;",
                "Landroidx/compose/ui/Modifier;",
                "Ljava/util/List;",
                "Lkotlin/jvm/functions/Function0;",
                "Lkotlin/jvm/functions/Function0;",
                "Lkotlin/jvm/functions/Function1;",
            ).map { type -> ImmutableMethodParameter(type, emptySet(), null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            implementation,
        )
    }

    @Test
    fun `raw candidate selection is rejected`() {
        val findings = lint("val selected = candidates.first()")

        assertEquals(listOf(ResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
    }

    @Test
    fun `guarded single on filtered object types is reported`() {
        val findings =
            lint(
                """
                fun hasObjectAndIntParameterShape(parameterTypes: List<CharSequence>, objectType: String? = null): Boolean {
                    val descriptors = parameterTypes.map(CharSequence::toString)
                    val objectTypes = descriptors.filter { it.startsWith("L") }
                    return descriptors.size == 2 &&
                        objectTypes.size == 1 &&
                        (objectType == null || objectTypes.single() == objectType) &&
                        descriptors.count { it == "I" } == 1
                }
                """.trimIndent(),
            )

        assertEquals(listOf(ResolverLinter.Rule.RAW_SINGLE), findings.map { it.rule })
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
                ResolverLinter.Rule.RAW_FIND,
                ResolverLinter.Rule.RAW_LAST,
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

        assertEquals(listOf(ResolverLinter.Rule.RAW_INDEX), findings.map { it.rule })
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
                ResolverLinter.Rule.RAW_FIRST,
                ResolverLinter.Rule.RAW_LAST,
                ResolverLinter.Rule.RAW_FIND,
                ResolverLinter.Rule.RAW_SINGLE,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `singleOrNull safe call is rejected as nullable fallthrough`() {
        val findings = lint("candidates.singleOrNull()?.patch()")

        assertEquals(listOf(ResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
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

        assertEquals(listOf(ResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
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
                ResolverLinter.Rule.NULLABLE_FIRST,
                ResolverLinter.Rule.NULLABLE_LAST,
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

        assertTrue(findings.any { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
        assertTrue(findings.size == 2, "actual=$findings")
        assertEquals(1, findings.first { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }.line)
    }

    @Test
    fun `implicit mapNotNull filtering is also reported`() {
        val findings =
            lint(
                """
                val selected = methods.mapNotNull { it.takeIf { method -> method.isTarget } }.first()
                """.trimIndent(),
            )

        assertTrue(findings.any { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
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

        assertEquals(listOf(ResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
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
    fun `typed hook without an explicit branch policy is reported`() {
        // Regression for the 12.29.0-alpha.04 inline-download-button abort: the fail-closed guard
        // stayed silent on 12.28.0-prod.01, so the omission only surfaced on the newer APK.
        val findings =
            lint(
                """
                method.insertHook(
                    index = 0,
                ) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(ResolverLinter.Rule.TYPED_HOOK_POLICY),
            findings.map { it.rule },
        )
        assertEquals(1, findings.single().line)
    }

    @Test
    fun `typed hook with an explicit branch policy is accepted`() {
        val findings =
            lint(
                """
                method.insertHook(index = 0, relocateBranchTargets = false) {
                    returnVoid()
                }
                method.insertHook(
                    index = 1,
                    relocateBranchTargets = true,
                ) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook policy finding can be suppressed with a directive`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow typed-hook-policy because the guard is exercised here
                method.insertHook(0) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook declaration and mentions are not call sites`() {
        val findings =
            lint(
                """
                // insertHook(0) without the policy is a call, not this comment.
                val documentation = "insertHook(index = 0)"
                internal fun MutableMethod.insertHook(
                    index: Int,
                    block: Block.() -> Unit,
                ): Insertion = error("fixture")
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook policy is matched inside a nested argument list`() {
        val findings =
            lint(
                """
                method.insertHook(index = helper.resolve(0, 1)) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(ResolverLinter.Rule.TYPED_HOOK_POLICY),
            findings.map { it.rule },
        )
    }

    @Test
    fun `rigid fingerprint parameter list is reported`() {
        val findings =
            lint(
                """
                Fingerprint(
                    parameters = listOf(
                        "Lcom/x/urt/items/post/j5;",
                        "Landroidx/compose/foundation/layout/b3;",
                        "Lcom/x/urt/items/post/p5;",
                        "Landroidx/compose/ui/Modifier;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                    ),
                )
                """.trimIndent(),
            )

        assertEquals(listOf(ResolverLinter.Rule.RIGID_SIGNATURE), findings.map { it.rule })
        assertTrue(!ResolverLinter.Rule.RIGID_SIGNATURE.advisory)
    }

    @Test
    fun `short parameter list without compose descriptors is not rigid`() {
        val findings =
            lint(
                """val fingerprint = Fingerprint(parameters = listOf("Z", navigationType, tabType))""",
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `fingerprint documentation example is not a rigid finding`() {
        val findings =
            lint(
                """
                // parameters = listOf("Landroidx/compose/ui/Modifier;", "I", "I", "I")
                val doc = "parameters = listOf(\"L\", \"L\", \"L\", \"L\")"
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `exact interface descriptor equality is reported`() {
        val findings = lint("""if (method.returnType == "Ljava/util/Set;") return candidate""")

        assertEquals(
            listOf(ResolverLinter.Rule.EXACT_INTERFACE_TYPE),
            findings.map { it.rule },
        )
        assertTrue(!ResolverLinter.Rule.EXACT_INTERFACE_TYPE.advisory)
    }

    @Test
    fun `assignability checks are not exact-interface findings`() {
        val findings =
            lint(
                """
                val setLike = descriptor == SET_DESCRIPTOR ||
                    classDef.interfaces.any { it.toString() == SET_DESCRIPTOR }
                classDef.methods.filter { method -> method.returnType.toString() != OBJECT_DESCRIPTOR }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `single-hop register containment is reported`() {
        val findings = lint("val feeds = instruction.registersUsed.contains(prefetchRegister)")

        assertEquals(
            listOf(ResolverLinter.Rule.SINGLE_HOP_REGISTER),
            findings.map { it.rule },
        )
        assertTrue(!ResolverLinter.Rule.SINGLE_HOP_REGISTER.advisory)
    }

    @Test
    fun `literal register containment is not a single-hop finding`() {
        val findings = lint("val usesFirst = instruction.registersUsed.contains(0)")

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `propagated register sets are not single-hop findings`() {
        val findings =
            lint(
                """
                val feeds = instruction.registersUsed.any { register -> register in seededRegisters }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `anchor finding can be suppressed`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow rigid-signature because this fingerprint pins the ABI on purpose
                val fingerprint = Fingerprint(parameters = listOf("L", "L", "L", "L"))
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `existing cardinality rules remain gating`() {
        assertTrue(!ResolverLinter.Rule.RAW_FIRST.advisory)
        assertTrue(!ResolverLinter.Rule.TYPED_HOOK_POLICY.advisory)
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

            val firstRun = ResolverLinter.lintDirectory(root)
            val secondRun = ResolverLinter.lintDirectory(root)

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
                    ResolverLinter.lintDirectory(root)
                }

            assertTrue(exception.message.orEmpty().contains("No Kotlin resolver sources"))
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
    fun `module divider selection follows reordered Compose wrapper and byte discriminator`() {
        val instructions = listOf(
            "new-instance v1, Lfixture/DividerAdapter;".toInstruction(),
            "const/16 v2, 0x7".toInstruction(),
            "invoke-direct {v1, v3, v2}, Lfixture/DividerAdapter;-><init>(Ljava/lang/Object;B)V".toInstruction(),
            "new-instance v4, Landroidx/compose/runtime/internal/g;".toInstruction(),
            "const/4 v5, 0x1".toInstruction(),
            "invoke-direct {v4, v5, v6, v1}, Landroidx/compose/runtime/internal/g;-><init>(IZLjava/lang/Object;)V".toInstruction(),
            "const/4 v7, 0x3".toInstruction(),
            "const/4 v0, 0x0".toInstruction(),
            "invoke-static {v8, v0, v4, v7}, Landroidx/compose/foundation/lazy/k;->u(Landroidx/compose/foundation/lazy/k;Ljava/lang/Object;Lkotlin/jvm/functions/Function3;I)V".toInstruction(),
        )

        val selected = instructions.timelineModuleDividerItemIndices(::dividerAdapterCaseBlock)

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
    fun `reply facepile list read must trace to a parameter`() {
        // 12.30.0-prod.01 `Lcom/bumptech/glide/h;->a(...)`: the renderer aliases the post model
        // parameter (v4) into v1 before reading its avatar list off the model.
        val aliasedParameter =
            listOf(
                "move-object v1, v4".toInstruction(),
                "iget-object v0, v1, Lfixture/PostState;->a:Ljava/util/ArrayList;".toInstruction(),
            )
        assertTrue(
            aliasedParameter.readsArrayListFromParameter(
                readIndex = 1,
                firstParameterRegister = 4,
                registerCount = 6,
            ),
        )

        // A direct read off the parameter register is the same contract.
        val directParameter =
            listOf(
                "iget-object v0, v4, Lfixture/PostState;->a:Ljava/util/ArrayList;".toInstruction(),
            )
        assertTrue(
            directParameter.readsArrayListFromParameter(
                readIndex = 0,
                firstParameterRegister = 4,
                registerCount = 6,
            ),
        )

        // 12.30.0-prod.01's `Lcom/x/mlb/game/ui/stats/a;->r(...)` false positive: v1 once aliased a
        // parameter, but an unrelated iterator result overwrites it before the local model read.
        val overwrittenAlias =
            listOf(
                "move-object v1, v4".toInstruction(),
                "invoke-interface {v2}, Lfixture/Replies;->iterator()Ljava/util/Iterator;".toInstruction(),
                "move-result-object v1".toInstruction(),
                "iget-object v3, v1, Lfixture/MlbModel;->a:Ljava/util/ArrayList;".toInstruction(),
            )
        assertFalse(
            overwrittenAlias.readsArrayListFromParameter(
                readIndex = 3,
                firstParameterRegister = 4,
                registerCount = 6,
            ),
        )
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

    @Test
    fun `media tab sub-tab seed ignores the Boolean FALSE post-sorting seed`() {
        val instructions =
            listOf(
                (
                    "invoke-static {v1}, Lkotlinx/coroutines/flow/z;->c(" +
                        "Ljava/lang/Object;)Lkotlinx/coroutines/flow/w2;"
                ).toInstruction(),
                "sget-object v2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;".toInstruction(),
                (
                    "invoke-static {v2}, Lkotlinx/coroutines/flow/z;->c(" +
                        "Ljava/lang/Object;)Lkotlinx/coroutines/flow/w2;"
                ).toInstruction(),
            )

        assertEquals(
            listOf(0),
            instructions.indices.filter { index -> isInitialSubTabSeed(instructions, index) },
        )
    }

    @Test
    fun `media tab pending sub-tab store matches only the tab-type field write`() {
        val tabType = "Lcom/x/profile/s;"
        val pendingStore =
            "iput-object v6, v4, Lcom/x/profile/timeline/d;->r:Lcom/x/profile/s;".toInstruction()
        val sortingSeedStore =
            "iput-object v6, v4, Lcom/x/profile/timeline/d;->s:Lkotlinx/coroutines/flow/w2;"
                .toInstruction()
        val tabListStore =
            "iput-object v5, v4, Lcom/x/profile/timeline/d;->a:Ljava/util/List;".toInstruction()

        assertEquals(
            listOf(true, false, false),
            listOf(pendingStore, sortingSeedStore, tabListStore).map { instruction ->
                isPendingSubTabStore(instruction, tabType)
            },
        )
    }

    @Test
    fun `inline-action kind model constructor is the three-parameter enum model`() {
        val kindConstructor =
            "invoke-direct {v3, v12, v4, v13}, Lcom/x/inlineactionbar/d1;-><init>(ZZLcom/x/inlineactionbar/f1;)V"
                .toInstruction()
        val layoutConstructor =
            "invoke-direct {v3, v4, v5, v4, v15}, Landroidx/compose/foundation/layout/b3;-><init>(FFFF)V"
                .toInstruction()
        val objectConstructor =
            "invoke-direct {v3, v12, v4, v13}, Lcom/x/inlineactionbar/d1;-><init>(ZZLjava/lang/Object;)V"
                .toInstruction()

        fun isKindEnum(descriptor: String) = descriptor == "Lcom/x/inlineactionbar/f1;"

        assertEquals(
            listOf(true, false, false),
            listOf(kindConstructor, layoutConstructor, objectConstructor).map { instruction ->
                isInlineActionKindModelConstructor(instruction, ::isKindEnum)
            },
        )
    }

    @Test
    fun `note post text field resolver survives outlined formatter order`() {
        fun constString(register: Int, value: String) =
            ("""const-string v$register, "$value""" + '"').toInstruction()

        val bodyToString =
            ImmutableMethod(
                "Lfixture/NotePostBody;",
                "toString",
                emptyList<ImmutableMethodParameter>(),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                MethodImplementationBuilder(5).apply {
                    addInstruction("new-instance v0, Ljava/lang/StringBuilder;".toInstruction())
                    addInstruction(constString(1, "NotePost(postEntityList="))
                    addInstruction(
                        "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V"
                            .toInstruction(),
                    )
                    addInstruction(
                        "iget-object v1, p0, Lfixture/NotePostBody;->postEntityList:Lfixture/PostEntityList;"
                            .toInstruction(),
                    )
                    addInstruction(
                        "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
                            .toInstruction(),
                    )
                    addInstruction(constString(1, ", richTextEntity="))
                    addInstruction(
                        "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                            .toInstruction(),
                    )
                    addInstruction(
                        "iget-object v1, p0, Lfixture/NotePostBody;->richTextEntity:Ljava/util/List;"
                            .toInstruction(),
                    )
                    addInstruction(
                        "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
                            .toInstruction(),
                    )
                    // R8 emits the text label before the inline-media label, but delays reading
                    // the String field until it invokes the formatter helper.
                    addInstruction(constString(1, ", text="))
                    addInstruction(
                        "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                            .toInstruction(),
                    )
                    addInstruction(constString(1, ", inlineMedia="))
                    addInstruction(constString(2, ")"))
                    addInstruction(
                        "iget-object v3, p0, Lfixture/NotePostBody;->text:Ljava/lang/String;"
                            .toInstruction(),
                    )
                    addInstruction(
                        "iget-object p0, p0, Lfixture/NotePostBody;->inlineMedia:Ljava/util/List;"
                            .toInstruction(),
                    )
                    addInstruction(
                        (
                            "invoke-static {v3, v1, v2, v0, p0}, " +
                            "Lfixture/NotePostFormatter;->format(" +
                            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
                            "Ljava/lang/StringBuilder;Ljava/util/List;)Ljava/lang/String;"
                        ).toInstruction(),
                    )
                    addInstruction("move-result-object p0".toInstruction())
                    addInstruction("return-object p0".toInstruction())
                }.methodImplementation,
            )

        val expected =
            ImmutableFieldReference(
                "Lfixture/NotePostBody;",
                "text",
                "Ljava/lang/String;",
            )
        assertEquals(expected.toString(), bodyToString.resolveNotePostBodyTextField().toString())
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

    private val replyEnum = "Lfixture/RankingMode;"
    private val tabHorizon = "Lcom/x/ui/horizon/Palette;"
    private val xdsColors = "Lcom/x/ui/xds/XdsColors;"
    private val backgroundMethod =
        "Landroidx/compose/foundation/BackgroundKt;->background(Landroidx/compose/ui/Modifier;JLandroidx/compose/ui/graphics/Shape;)Landroidx/compose/ui/Modifier;"

    @Test
    fun `reply sorting selection resolves the validated cast-to-store tuple through every positive form`() {
        for (shape in listOf("adjacent", "entryMove", "multiHop", "returningOverwrite", "unrelatedStore", "postCastMove", "tryUnrelated")) {
            val method = replySelectionFixture(shape)
            val instructions = method.implementation!!.instructions.toList()
            val site = resolveReplySortingSelectionSite(method, replyEnum, method.defaultUrtIndex())
            assertEquals(
                instructions.subList(0, method.defaultUrtIndex()).indexOfLast { it.opcode == Opcode.IPUT_OBJECT },
                site.storeIndex,
                "$shape must select the store on the defaultUrt path, not an earlier returning arm",
            )
            assertEquals(Opcode.CHECK_CAST, instructions[site.castIndex].opcode, shape)
            assertEquals(replyEnum, ((instructions[site.castIndex] as ReferenceInstruction).reference as TypeReference).type, shape)
            assertEquals(Opcode.IPUT_OBJECT, instructions[site.storeIndex].opcode, shape)
            assertEquals(site.register, (instructions[site.storeIndex] as TwoRegisterInstruction).registerA, shape)
            assertTrue(site.storeIndex < method.defaultUrtIndex(), shape)
        }
    }

    @Test
    fun `reply sorting selection rejects wrong enum, reaching clobber, missing store, and ambiguous tuples`() {
        for (shape in listOf("wrongEnum", "clobber", "noStore", "twoTuples", "onlyUnrelatedStore", "tryClobber")) {
            val method = replySelectionFixture(shape)
            assertFailsWith<PatchException>(shape) {
                resolveReplySortingSelectionSite(method, replyEnum, method.defaultUrtIndex())
            }
        }
    }

    @Test
    fun `reply sorting hook remembers the validated enum immediately before its store and keeps incoming branches on it`() {
        val method = MutableMethod(replySelectionFixture("adjacent"))
        val site = resolveReplySortingSelectionSite(method, replyEnum, method.defaultUrtIndex())

        method.installReplySortingSelectionHook(site)

        val code = method.instructions.toList()
        val store = code.indexOfFirst { it.opcode == Opcode.IPUT_OBJECT }
        assertEquals(site.storeIndex + 1, store, "the original store must shift past the inserted call")
        val remember = code[store - 1]
        assertEquals(Opcode.INVOKE_STATIC, remember.opcode)
        val reference = (remember as ReferenceInstruction).reference as MethodReference
        assertEquals("remember", reference.name)
        assertEquals(listOf("Ljava/lang/Object;"), reference.parameterTypes.map(CharSequence::toString))
        assertEquals(listOf(site.register), remember.registersUsed)
        assertEquals(site.register, (code[store] as TwoRegisterInstruction).registerA)
        val branch = code.first { it.opcode == Opcode.IF_EQZ } as BuilderOffsetInstruction
        assertEquals(store - 1, branch.target.location.index, "the incoming branch must run remember before the store")
    }

    private fun Method.defaultUrtIndex(): Int = implementation!!.instructions.indexOfFirst { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == "defaultUrtTimelineComponent"
    }

    private fun replySelectionFixture(shape: String): ImmutableMethod {
        val registers = 240
        val parameter = registers - 1
        val holder = "Lfixture/ReplyHolder;"
        val implementation = MethodImplementationBuilder(registers).apply {
            fun add(instruction: String) = addInstruction(instruction.toInstruction())
            fun endSelection() {
                add("const-string v6, \"defaultUrtTimelineComponent\"")
                add("return-void")
            }
            // Copies the receiver into a low register and branches on a field read, so both arms are reachable.
            fun unknownBranch(label: String) {
                add("move-object/from16 v7, v238")
                add("iget-boolean v5, v7, Lfixture/ReplySortingCallback;->inlined:Z")
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 5, getLabel(label)))
            }
            // Unrelated arm 0 casts and stores the enum before the anchor, then returns; the selected
            // fallthrough arm may or may not store it. Numeric order alone must not select arm 0.
            fun unrelatedArmLayout(selectedStore: Boolean) {
                add("move-object/from16 v0, v$parameter")
                add("move-object/from16 v7, v238")
                add("iget-byte v2, v7, Lfixture/ReplySortingCallback;->selector:B")
                addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 2, getLabel("payload")))
                addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("selected")))
                addLabel("unrelated0")
                add("check-cast v0, $replyEnum")
                add("iput-object v0, v1, $holder->value:Ljava/lang/Object;")
                add("return-void")
                addLabel("selected")
                if (selectedStore) {
                    add("check-cast v0, $replyEnum")
                    add("iput-object v0, v1, $holder->value:Ljava/lang/Object;")
                }
                add("const-string v6, \"defaultUrtTimelineComponent\"")
                add("return-void")
                addLabel("unrelated1")
                add("return-void")
                addLabel("payload")
                addInstruction(BuilderPackedSwitchPayload(0, listOf(getLabel("unrelated0"), getLabel("unrelated1"))))
            }
            add("new-instance v1, $holder")
            when (shape) {
                "unrelatedStore" -> unrelatedArmLayout(selectedStore = true)
                "onlyUnrelatedStore" -> unrelatedArmLayout(selectedStore = false)
                "adjacent" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    unknownBranch("store")
                    add("nop")
                    addLabel("store")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                "entryMove" -> {
                    add("move-object/from16 v0, v$parameter")
                    add("move-object/from16 v7, v238")
                    add("iget-byte v2, v7, Lfixture/ReplySortingCallback;->selector:B")
                    addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 2, getLabel("payload")))
                    add("check-cast v0, $replyEnum")
                    add("iput-object v0, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                    addLabel("unrelated0")
                    add("const/4 v0, 0x0")
                    add("return-void")
                    addLabel("unrelated1")
                    add("const/4 v0, 0x1")
                    add("return-void")
                    addLabel("payload")
                    addInstruction(BuilderPackedSwitchPayload(0, listOf(getLabel("unrelated0"), getLabel("unrelated1"))))
                }
                "multiHop" -> {
                    add("move-object/16 v236, v$parameter")
                    add("move-object/from16 v200, v236")
                    add("move-object/from16 v3, v200")
                    add("check-cast v3, $replyEnum")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                "returningOverwrite" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    unknownBranch("overwrite")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                    addLabel("overwrite")
                    add("const/4 v3, 0x0")
                    add("return-void")
                }
                "postCastMove" -> {
                    add("move-object/16 v200, v$parameter")
                    add("check-cast v200, $replyEnum")
                    add("move-object/from16 v3, v200")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                "tryUnrelated", "tryClobber" -> {
                    // An unrelated call may throw into a handler that rejoins the store; only the clobbering handler breaks the alias.
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    addLabel("tryStart")
                    add("invoke-static {}, Lfixture/ReplyUnrelated;->ping()V")
                    addLabel("tryEnd")
                    addLabel("store")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                    addLabel("catch")
                    if (shape == "tryClobber") add("const/4 v3, 0x0")
                    addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("store")))
                    addCatch("Ljava/lang/Throwable;", getLabel("tryStart"), getLabel("tryEnd"), getLabel("catch"))
                }
                "wrongEnum" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, Lfixture/OtherMode;")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                "clobber" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    unknownBranch("store")
                    add("const/4 v3, 0x0")
                    addLabel("store")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                "noStore" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    endSelection()
                }
                "twoTuples" -> {
                    add("move-object/from16 v3, v$parameter")
                    add("check-cast v3, $replyEnum")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    add("check-cast v3, $replyEnum")
                    add("iput-object v3, v1, $holder->value:Ljava/lang/Object;")
                    endSelection()
                }
                else -> error("unknown reply selection shape $shape")
            }
        }
        return ImmutableMethod(
            "Lfixture/ReplySortingCallback;", "invoke",
            listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)), "V",
            AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation.methodImplementation,
        )
    }

    @Test
    fun `tab indicator tint follows the registry callback into the selected lambda arm across moved owners and selectors`() {
        for ((lambdaType, selector) in listOf("Lfixture/tabs/LambdaA;" to 0x2a, "Lfixture/chrome/LambdaB;" to 7)) {
            for (shape in listOf("plain", "moved", "range")) {
                val (slot, classes) = tabIndicatorFixture(lambdaType, selector, shape)
                val description = "$lambdaType $shape"
                val site = resolveTabIndicatorTintSite(slot, tabHorizon, classes::get)
                val invoke = classes.getValue(lambdaType).methods.single { it.name == "invoke" }
                val instructions = invoke.implementation!!.instructions.toList()
                assertEquals(invoke, site.method, description)
                assertEquals(Opcode.SGET_WIDE, instructions[site.loadIndex].opcode, description)
                assertTrue(
                    (instructions[site.loadIndex] as ReferenceInstruction).reference.toString().endsWith("$xdsColors->brand:J"),
                    description,
                )
                assertEquals((instructions[site.loadIndex] as OneRegisterInstruction).registerA, site.colorRegister, description)
                assertEquals(Opcode.INVOKE_STATIC, instructions[site.callIndex].opcode, description)
                assertEquals(
                    "background",
                    ((instructions[site.callIndex] as ReferenceInstruction).reference as MethodReference).name,
                    description,
                )
                assertTrue(site.callIndex > site.loadIndex, description)
            }
        }
    }

    @Test
    fun `tab indicator tint rejects disconnected overwritten and ambiguous callback paths`() {
        for (variant in listOf(
            "disconnected", "overwritten", "wrongSelector", "clobbered", "brandWrongCase", "ambiguous",
            "mutableSelector", "statelessSwitch", "mutableCallback", "overwrittenSelector", "skippedSelector",
            "catchClobber", "tryConstructor", "crossCaseCatch",
        )) {
            val (slot, classes) = tabIndicatorFixture("Lfixture/tabs/LambdaA;", 0x2a, "plain", variant)
            assertFailsWith<PatchException>(variant) {
                resolveTabIndicatorTintSite(slot, tabHorizon, classes::get)
            }
        }
    }

    /**
     * Slot -> registry `<clinit>` -> Compose wrapper -> selector-driven lambda. [shape] moves the
     * callback through copies or a range call and places a distant wide load. [variant] breaks one
     * link of the chain for the negative cases.
     */
    private fun tabIndicatorFixture(
        lambdaType: String,
        selector: Int,
        shape: String,
        variant: String = "ok",
    ): Pair<Method, Map<String, ImmutableClassDef>> {
        val registry = "Lfixture/tabs/TabIndicatorRegistry;"
        val wrapper = "Landroidx/compose/runtime/internal/ComposableLambdaImpl;"
        val composer = "Landroidx/compose/runtime/Composer;"
        val modifier = "Landroidx/compose/ui/Modifier;"
        val objectType = "Ljava/lang/Object;"
        val animation =
            "Landroidx/compose/animation/AnimatedVisibilityKt;->AnimatedVisibility(ZLkotlin/jvm/functions/Function3;$composer$modifier" +
                "I)V"
        val written = if (variant == "disconnected") "offstage" else "indicator"
        val field = "$registry->indicator:$objectType"
        val publicStatic = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value

        fun method(
            owner: String,
            name: String,
            parameters: List<String>,
            returnType: String,
            registers: Int,
            static: Boolean,
            body: MethodImplementationBuilder.() -> Unit,
        ) = ImmutableMethod(
            owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returnType,
            AccessFlags.PUBLIC.value or (if (static) AccessFlags.STATIC.value else 0) or
                (if (name == "<init>" || name == "<clinit>") AccessFlags.CONSTRUCTOR.value else 0),
            emptySet(), emptySet(), MethodImplementationBuilder(registers).apply(body).methodImplementation,
        )
        fun MethodImplementationBuilder.add(instruction: String) = addInstruction(instruction.toInstruction())

        val slot = method(
            "Lfixture/tabs/TabSlots;", "slot", listOf("Z", composer, modifier, "I"), "V", 9, true,
        ) {
            when (shape) {
                "range" -> {
                    add("move v4, v0")
                    add("sget-object v5, $field")
                    add("move-object v6, v1")
                    add("move-object v7, v2")
                    add("move v8, v3")
                    add("invoke-static/range {v4 .. v8}, $animation")
                }
                "moved" -> {
                    add("sget-object v5, $field")
                    add("move-object v6, v5")
                    add("move-object v4, v6")
                    add("invoke-static {v0, v4, v1, v2, v3}, $animation")
                }
                else -> {
                    add("sget-object v4, $field")
                    add("invoke-static {v0, v4, v1, v2, v3}, $animation")
                }
            }
            add("return-void")
        }

        val clinit = method(registry, "<clinit>", emptyList(), "V", 6, true) {
            add("new-instance v0, $lambdaType")
            if (variant == "statelessSwitch") {
                add("invoke-direct {v0}, $lambdaType-><init>()V")
            } else {
                add("const/16 v1, ${if (variant == "wrongSelector") selector + 5 else selector}")
                add("invoke-direct {v0, v1}, $lambdaType-><init>(B)V")
            }
            add("new-instance v2, $wrapper")
            add("const/4 v3, 0x0")
            add("const/4 v4, 0x1")
            add("invoke-direct {v2, v3, v4, v0}, $wrapper-><init>(IZLjava/lang/Object;)V")
            add("sput-object v2, $registry->$written:$objectType")
            if (variant == "overwritten") {
                add("const/4 v5, 0x0")
                add("sput-object v5, $registry->$written:$objectType")
            }
            add("return-void")
        }

        val standardInvoke = method(
            lambdaType, "invoke", listOf(objectType, objectType, objectType), objectType, 16, false,
        ) {
            add("const/4 v8, 0x0")
            add("iget-byte v0, v12, $lambdaType->selector:B")
            addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, getLabel("payload")))
            add("const/4 v1, 0x0")
            add("return-object v1")
            val selectedRead =
                if (variant == "brandWrongCase") "$tabHorizon->surface:J" else "$xdsColors->brand:J"
            val otherRead = when (variant) {
                "brandWrongCase" -> "$xdsColors->brand:J"
                "statelessSwitch" -> "$tabHorizon->surface:J"
                else -> "$xdsColors->other:J"
            }
            addLabel("selected")
            add("check-cast v13, $modifier")
            add("sget-wide v2, $selectedRead")
            if (variant == "clobbered") add("const-wide/16 v2, 0x0")
            if (shape == "moved") {
                repeat(3) { add("nop") }
                add("move-wide/from16 v10, v2")
            }
            val (low, high) = if (shape == "moved") 10 to 11 else 2 to 3
            add("invoke-static {v13, v$low, v$high, v8}, $backgroundMethod")
            add("move-result-object v13")
            if (variant == "ambiguous") {
                add("sget-wide v2, $selectedRead")
                add("invoke-static {v13, v2, v3, v8}, $backgroundMethod")
                add("move-result-object v13")
            }
            add("return-object v13")
            addLabel("other")
            add("check-cast v13, $modifier")
            add("sget-wide v2, $otherRead")
            add("invoke-static {v13, v2, v3, v8}, $backgroundMethod")
            add("move-result-object v13")
            add("return-object v13")
            addLabel("payload")
            val firstKey = if (variant == "statelessSwitch") 1 else selector
            addInstruction(BuilderPackedSwitchPayload(firstKey, listOf(getLabel("selected"), getLabel("other"))))
        }

        val wrapperClass = ImmutableClassDef(
            wrapper, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(),
            listOf(method(wrapper, "<init>", listOf("I", "Z", objectType), "V", 4, false) { add("return-void") }),
        )
        // Catch variants keep the same arms: the selected call sits in a try whose handler either preserves or clobbers
        // the color register before rejoining the call, and the other arm carries an unrelated try.
        val invoke = if (variant.startsWith("catch") || variant == "crossCaseCatch") {
            method(lambdaType, "invoke", listOf(objectType, objectType, objectType), objectType, 16, false) {
                add("const/4 v8, 0x0")
                add("iget-byte v0, v12, $lambdaType->selector:B")
                addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, getLabel("payload")))
                add("const/4 v1, 0x0")
                add("return-object v1")
                addLabel("selected")
                add("check-cast v13, $modifier")
                add("sget-wide v2, $xdsColors->brand:J")
                addLabel("call")
                addLabel("tryStart")
                add("invoke-static {v13, v2, v3, v8}, $backgroundMethod")
                addLabel("tryEnd")
                add("move-result-object v13")
                add("return-object v13")
                addLabel("catch")
                if (variant == "catchClobber") add("const-wide/16 v2, 0x0")
                addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("call")))
                addLabel("other")
                add("check-cast v13, $modifier")
                add("sget-wide v2, $xdsColors->other:J")
                addLabel("otherStart")
                add("invoke-static {v13, v2, v3, v8}, $backgroundMethod")
                addLabel("otherEnd")
                add("move-result-object v13")
                add("return-object v13")
                addLabel("otherCatch")
                if (variant == "crossCaseCatch") {
                    // The other case's handler enters the selected case's color call, sharing its scope.
                    addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("call")))
                } else {
                    add("const/4 v1, 0x0")
                    add("return-object v1")
                }
                addLabel("payload")
                addInstruction(BuilderPackedSwitchPayload(selector, listOf(getLabel("selected"), getLabel("other"))))
                addCatch("Ljava/lang/Throwable;", getLabel("tryStart"), getLabel("tryEnd"), getLabel("catch"))
                addCatch("Ljava/lang/Throwable;", getLabel("otherStart"), getLabel("otherEnd"), getLabel("otherCatch"))
            }
        } else {
            standardInvoke
        }
        // The synthetic selector is written only by its constructor, so it is final unless a variant says otherwise.
        val selectorAccess =
            AccessFlags.PRIVATE.value or (if (variant == "mutableSelector") 0 else AccessFlags.FINAL.value)
        val constructor = if (variant == "statelessSwitch") {
            method(lambdaType, "<init>", emptyList(), "V", 2, false) {
                add("const/4 v0, 0x0")
                add("iput-byte v0, v1, $lambdaType->selector:B")
                add("return-void")
            }
        } else if (variant == "tryConstructor") {
            // The only selector write sits in a try, so its dominance over the normal return is not proven.
            method(lambdaType, "<init>", listOf("B"), "V", 3, false) {
                addLabel("tryStart")
                add("iput-byte v2, v1, $lambdaType->selector:B")
                addLabel("tryEnd")
                add("return-void")
                addLabel("catch")
                add("return-void")
                addCatch("Ljava/lang/Throwable;", getLabel("tryStart"), getLabel("tryEnd"), getLabel("catch"))
            }
        } else {
            method(lambdaType, "<init>", listOf("B"), "V", 3, false) {
                // A nonzero selector returns before the only write, leaving the final field at its default 0.
                if (variant == "skippedSelector") {
                    addInstruction(BuilderInstruction21t(Opcode.IF_NEZ, 2, getLabel("return")))
                }
                add("iput-byte v2, v1, $lambdaType->selector:B")
                if (variant == "overwrittenSelector") {
                    // A second constructor write leaves the final field at 0 on every instance.
                    add("const/4 v0, 0x0")
                    add("iput-byte v0, v1, $lambdaType->selector:B")
                }
                if (variant == "skippedSelector") addLabel("return")
                add("return-void")
            }
        }
        val lambdaClass = ImmutableClassDef(
            lambdaType, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, "Ljava/lang/Object;",
            listOf("Lkotlin/jvm/functions/Function3;"), null, emptySet(),
            listOf(ImmutableField(lambdaType, "selector", "B", selectorAccess, null, emptySet(), emptySet())),
            listOf(constructor, invoke),
        )
        // The callback cache is a static singleton written once by <clinit>, so it is final unless a variant says otherwise.
        val callbackAccess =
            publicStatic or (if (variant == "mutableCallback") 0 else AccessFlags.FINAL.value)
        val registryClass = ImmutableClassDef(
            registry, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null, emptySet(),
            listOf(
                ImmutableField(registry, "indicator", objectType, callbackAccess, null, emptySet(), emptySet()),
                ImmutableField(registry, "offstage", objectType, callbackAccess, null, emptySet(), emptySet()),
            ),
            listOf(clinit),
        )
        return slot to mapOf(registry to registryClass, wrapper to wrapperClass, lambdaType to lambdaClass)
    }

    @Test
    fun `tab indicator tint survives an unrelated try and a catch that preserves the color alias`() {
        val lambdaType = "Lfixture/tabs/LambdaA;"
        val (slot, classes) = tabIndicatorFixture(lambdaType, 0x2a, "plain", "catchPreserves")
        val site = resolveTabIndicatorTintSite(slot, tabHorizon, classes::get)
        val invoke = classes.getValue(lambdaType).methods.single { it.name == "invoke" }
        val instructions = invoke.implementation!!.instructions.toList()
        fun reference(index: Int) = (instructions[index] as? ReferenceInstruction)?.reference?.toString().orEmpty()
        assertEquals(
            instructions.indexOfFirst { it.opcode == Opcode.SGET_WIDE && reference(instructions.indexOf(it)).endsWith("$xdsColors->brand:J") },
            site.loadIndex,
        )
        assertEquals(
            instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.name == "background"
            },
            site.callIndex,
        )
    }

    @Test
    fun `wide source proof checks both words and rejects unrelated or overwritten sources on both proof paths`() {
        fun wide(vararg lines: String): Method = ImmutableMethod(
            "Lfixture/WideProof;", "probe", emptyList(), "J",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
            MethodImplementationBuilder(8).apply {
                lines.forEach { addInstruction(it.toInstruction()) }
            }.methodImplementation,
        )

        val intact = wide("const-wide v4, 0x1", "return-wide v4")
        assertTrue(intact.provesValueFromInstruction(0, 4, 1, 4))
        assertTrue(intact.provesValueFromInstruction(0, 5, 1, 5), "high word of a wide constant must be proven")
        assertTrue(intact.provesValueFromInstructionOnSourcePaths(0, 4, 1, 4))
        assertTrue(intact.provesValueFromInstructionOnSourcePaths(0, 5, 1, 5))
        assertFalse(intact.provesValueFromInstruction(0, 6, 1, 4), "requested source register must be the destination")
        assertFalse(intact.provesValueFromInstructionOnSourcePaths(0, 6, 1, 4))

        val overwritten = wide("const-wide v4, 0x1", "const/4 v5, 0x0", "return-wide v4")
        assertTrue(overwritten.provesValueFromInstruction(0, 4, 2, 4), "surviving low word stays proven")
        assertFalse(overwritten.provesValueFromInstruction(0, 5, 2, 5), "clobbered high word must not be proven")
        assertFalse(overwritten.provesValueFromInstructionOnSourcePaths(0, 5, 2, 5))

        val fieldLoad = wide("iget-wide v4, v0, Lfixture/WideHolder;->value:J", "return-wide v4")
        assertTrue(fieldLoad.provesValueFromInstruction(0, 4, 1, 4))
        assertTrue(fieldLoad.provesValueFromInstruction(0, 5, 1, 5))
    }

    private fun lint(source: String): List<ResolverLinter.Finding> =
        ResolverLinter.lintSource("Fixture.kt", source)
}
