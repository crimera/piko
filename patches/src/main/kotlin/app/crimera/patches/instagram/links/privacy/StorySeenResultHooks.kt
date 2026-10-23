/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.common.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val RESULT_METHOD = "pikoStorySeenSucceeded"

/**
 * Adds a method to the story request, which returns if the completed request succeeded,
 * and makes [extensionMethod] call it.
 */
context(context: BytecodePatchContext)
internal fun addStorySeenResultHooks(
    requestType: String,
    extensionMethod: MutableMethod,
) {
    val runMatch = storySeenRequestRunFingerprint(requestType).match()
    val taskField = runMatch.instructionMatches[0].fieldReference
    val resultGetter = runMatch.instructionMatches[2].methodReference
    val successType = runMatch.instructionMatches[5].instruction.getReference<TypeReference>()!!.type

    val resultMethod = ImmutableMethod(
        requestType,
        RESULT_METHOD,
        emptyList(),
        "Z",
        AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value,
        emptySet(),
        emptySet(),
        MutableMethodImplementation(3),
    ).toMutable().apply {
        addInstructions(
            0,
            """
            iget-object v0, p0, $taskField
            invoke-virtual {v0}, $resultGetter
            move-result-object v0
            instance-of v0, v0, $successType
            return v0
            """.trimIndent(),
        )
    }
    context.mutableClassDefBy(requestType).methods.add(resultMethod)

    extensionMethod.addInstructions(
        0,
        """
        instance-of p1, p0, $requestType
        if-eqz p1, :piko_story_result_unavailable
        check-cast p0, $requestType
        invoke-virtual {p0}, $requestType->$RESULT_METHOD()Z
        move-result p1
        return p1
        :piko_story_result_unavailable
        const/4 p1, 0x0
        return p1
        """.trimIndent(),
    )
}
