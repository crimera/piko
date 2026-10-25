/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.patches.instagram.utils.typeReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.writeRegister
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val STORY_FRAGMENT = "Linstagram/features/stories/fragment/ReelViewerFragment;"
private const val STORY_BAR_WRAPPER = "pikoSetTrayStorySystemBars"
private const val STORY_BAR_APPLY = "pikoApplyTrayStorySystemBars"
private val storyBarParameters = listOf(
    "Landroid/app/Activity;",
    "Landroidx/fragment/app/Fragment;",
    "Lcom/instagram/common/session/UserSession;",
    "Z", "Z", "Z", "Z",
)
private val storyBarApplyParameters = storyBarParameters.take(3) + "I" + storyBarParameters.drop(3)

private object StorySystemBarFingerprint : Fingerprint(
    definingClass = STORY_FRAGMENT,
    returnType = "V",
    filters = listOf(
        string("ReelViewerFragment.setDarkTheme"),
        methodCall(
            parameters = storyBarParameters,
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC_RANGE,
            location = MatchAfterWithin(20),
        ),
    ),
)

private object StorySourceFingerprint : Fingerprint(
    definingClass = STORY_FRAGMENT,
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(
        string("ReelViewerFragment.ARGUMENTS_KEY_EXTRA_SOURCE_MODULE"),
        opcode(Opcode.CHECK_CAST, MatchAfterWithin(6)),
        string("ReelViewerFragment.ARGUMENTS_KEY_EXTRA_SOURCE_CONTAINER_MODULE"),
    ),
)

private fun Method.copyStoryBarMethod(name: String) = MutableMethod(
    ImmutableMethod(
        definingClass, name, parameters, returnType, accessFlags,
        annotations, hiddenApiRestrictions, implementation,
    ),
)

private fun MutableMethod.replaceStoryBarCall(index: Int, target: MethodReference) {
    val instruction = getInstruction(index)
    val registers = instruction.registersUsed
    if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE ||
        registers.size != target.parameterTypes.size ||
        registers != (registers.first()..registers.last()).toList()
    ) {
        throw PatchException("Unexpected story system bar invocation: $this at $index")
    }
    replaceInstruction(
        index,
        "invoke-static/range {v${registers.first()} .. v${registers.last()}}, $target",
    )
}

context(patchContext: BytecodePatchContext)
internal fun installStorySystemBarHooks() {
    val sourceMatches = StorySourceFingerprint.instructionMatches
    val sourceType = sourceMatches[1].typeReference
    val sourceFields = StorySourceFingerprint.method.instructions
        .slice(sourceMatches[1].index + 1 until sourceMatches.last().index)
        .filter { it.opcode == Opcode.IPUT_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }
        .filter { it.definingClass == STORY_FRAGMENT && it.type == sourceType }
    if (sourceFields.size != 1) throw PatchException("Expected one story source field")
    val sourceField = sourceFields.single()
    val storyClass = patchContext.mutableClassDefBy(STORY_FRAGMENT)
    if (storyClass.fields.none { it.toString() == sourceField.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags) }) {
        throw PatchException("Story source field must be public")
    }
    val traySource = Fingerprint(
        definingClass = sourceType,
        name = "<clinit>",
        filters = listOf(
            string("MAIN_FEED_TRAY"),
            fieldAccess(
                definingClass = sourceType,
                type = sourceType,
                opcode = Opcode.SPUT_OBJECT,
                location = MatchAfterWithin(8),
            ),
        ),
    ).instructionMatches.last().fieldReference

    val wrapperReference = StorySystemBarFingerprint.instructionMatches.last().methodReference
    val wrapperFingerprint = Fingerprint(
        definingClass = wrapperReference.definingClass,
        name = wrapperReference.name,
        parameters = storyBarParameters,
        returnType = "V",
        filters = listOf(
            methodCall(
                definingClass = wrapperReference.definingClass,
                parameters = storyBarApplyParameters,
                returnType = "V",
                opcode = Opcode.INVOKE_STATIC_RANGE,
            ),
        ),
    )
    val applyCall = wrapperFingerprint.instructionMatches.single()
    val applyReference = applyCall.methodReference
    val backgroundFingerprint = Fingerprint(
        definingClass = applyReference.definingClass,
        name = applyReference.name,
        parameters = storyBarApplyParameters,
        returnType = "V",
        filters = listOf(
            literal(getResourceId(ResourceType.ATTR, "igds_color_primary_background")),
            methodCall(
                parameters = listOf("Landroid/content/Context;", "I"),
                returnType = "I",
                opcode = Opcode.INVOKE_STATIC,
                location = MatchAfterImmediately(),
            ),
            opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        ),
    )
    val backgroundAttribute = backgroundFingerprint.instructionMatches.first()
    val colorRegister = backgroundAttribute.instruction.registersUsed.single()

    // Reuse the story color excluded from Material You, leaving shared updates and restoration intact.
    val applyMethod = backgroundFingerprint.method.copyStoryBarMethod(STORY_BAR_APPLY).apply {
        replaceInstruction(
            backgroundAttribute.index,
            "const v$colorRegister, ${getResourceId(ResourceType.ATTR, "igds_color_stories_loading_background")}",
        )
    }
    val wrapperMethod = wrapperFingerprint.method.copyStoryBarMethod(STORY_BAR_WRAPPER).apply {
        val locals = implementation!!.registerCount - storyBarParameters.size
        if (locals < 2 || locals + 1 > 255) throw PatchException("Unexpected story bar wrapper registers")
        replaceStoryBarCall(applyCall.index, applyMethod)
        // The fragment is only used to select the color; native owner tracking still receives null.
        addInstructionsWithLabels(
            0,
            """
            move-object/from16 v0, p1
            check-cast v0, $STORY_FRAGMENT
            iget-object v0, v0, $sourceField
            sget-object v1, $traySource
            const/16 p1, 0x0
            if-eq v0, v1, :tray_story
            invoke-static/range {p0 .. p6}, $wrapperReference
            return-void
            """.trimIndent(),
            ExternalLabel("tray_story", getInstruction(0)),
        )
    }
    val owner = patchContext.mutableClassDefBy(wrapperReference.definingClass)
    if (owner.methods.any { it.name == STORY_BAR_WRAPPER || it.name == STORY_BAR_APPLY }) {
        throw PatchException("Story system bar methods already exist")
    }
    val calls = storyClass.methods.flatMap { method ->
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.getReference<MethodReference>()?.toString() == wrapperReference.toString()) {
                method to index
            } else null
        }
    }
    if (calls.size != 2) throw PatchException("Expected two story system bar calls, found ${calls.size}")

    owner.methods.addAll(listOf(applyMethod, wrapperMethod))
    calls.sortedByDescending { it.second }.forEach { (method, index) ->
        val beforeCall = method.instructions.take(index)
        val activityIndex = beforeCall.indexOfLast {
            it.getReference<MethodReference>()?.toString() ==
                "Landroidx/fragment/app/Fragment;->requireActivity()Landroidx/fragment/app/FragmentActivity;"
        }
        if (activityIndex < 0) throw PatchException("Missing story activity lookup")
        val fragmentRegister = beforeCall[activityIndex].registersUsed.single()
        val ownerRegister = method.getInstruction(index).registersUsed[1]
        val ownerWrite = beforeCall.lastOrNull { it.writeRegister == ownerRegister }
        if (ownerRegister > 15 || ownerWrite?.opcode != Opcode.CONST_4 ||
            (ownerWrite as? WideLiteralInstruction)?.wideLiteral != 0L ||
            beforeCall.drop(activityIndex + 1).any { it.writeRegister == fragmentRegister }
        ) {
            throw PatchException("Unexpected story system bar owner")
        }
        method.replaceStoryBarCall(index, wrapperMethod)
        method.addInstruction(index + 1, "const/4 v$ownerRegister, 0x0")
        method.addInstruction(index, "move-object/from16 v$ownerRegister, v$fragmentRegister")
    }
}
