/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val VIEW_DESCRIPTOR = "Landroid/view/View;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"
private const val REEL_ITEM_DESCRIPTOR = "Lcom/instagram/model/reels/ReelItem;"

val storyDownloadButtonPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        hookStoryToolbarBinder()
        enableSettings("storyDownloadButton")
    }
}

context(patchContext: BytecodePatchContext)
private fun hookStoryToolbarBinder() {
    val holder = Fingerprint(
        name = "<init>",
        returnType = "V",
        filters = listOf(resourceLiteral(ResourceType.ID, "toolbar_buttons_container")),
    )
    val instructions = holder.originalMethod.implementation!!.instructions.toList()
    val idIndex = holder.instructionMatches.single().index
    val resolveIndex = idIndex + 1
    val resolve = instructions.getOrNull(resolveIndex)?.getReference<MethodReference>()
    if (resolve?.returnType != VIEW_DESCRIPTOR ||
        instructions.getOrNull(resolveIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT
    ) throw PatchException("Unexpected story buttons container lookup in ${holder.originalMethod}")
    val containerRegister = instructions[resolveIndex + 1].registersUsed.single()
    val storeIndex = resolveIndex + if (instructions.getOrNull(resolveIndex + 2)?.opcode == Opcode.CHECK_CAST) 3 else 2
    val store = instructions.getOrNull(storeIndex)
    val containerField = store?.getReference<FieldReference>()
    if (store?.opcode != Opcode.IPUT_OBJECT || store.registersUsed.firstOrNull() != containerRegister ||
        containerField?.definingClass != holder.originalClassDef.type || containerField.type != VIEW_DESCRIPTOR
    ) throw PatchException("Unexpected story buttons container field in ${holder.originalMethod}")

    val mediaGetters = patchContext.classDefBy(REEL_ITEM_DESCRIPTOR).methods.filter {
        it.parameterTypes.isEmpty() && it.returnType == MEDIA_DESCRIPTOR && !AccessFlags.STATIC.isSet(it.accessFlags)
    }
    val mediaField = requireOne(
        "story Media field",
        mediaGetters.flatMap { it.implementation?.instructions?.toList().orEmpty() }
            .filter { it.opcode == Opcode.IGET_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass == REEL_ITEM_DESCRIPTOR && it.type == MEDIA_DESCRIPTOR }
            .distinctBy { it.toString() },
    )

    val candidates = mutableListOf<Method>()
    patchContext.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.returnType == "V" &&
                listOf(holder.originalClassDef.type, REEL_ITEM_DESCRIPTOR, USER_SESSION_DESCRIPTOR).all { type ->
                    method.parameterTypes.count { it.toString() == type } == 1
                }
            ) candidates += method
        }
    }
    // Sibling helpers take the same arguments; hook only the entry point that calls them.
    val called = candidates.flatMap { candidate ->
        candidate.implementation?.instructions?.mapNotNull { it.getReference<MethodReference>() }.orEmpty()
    }.map { it.toString() }.toSet()
    val entry = requireOne("story toolbar binder", candidates.filter { it.toString() !in called })
    val binder = requireOne(
        "mutable story toolbar binder",
        patchContext.mutableClassDefBy(entry.definingClass).methods.filter { it.toString() == entry.toString() },
    )
    if (!AccessFlags.STATIC.isSet(binder.accessFlags)) throw PatchException("Story toolbar binder is not static: $binder")

    val (container, media, session) = binder.reserveFreeRegisters(0, 3, 15)
    binder.addInstructions(
        0,
        """
        ${moveObject(container, binder.parameterRegister(holder.originalClassDef.type))}
        iget-object v$container, v$container, $containerField
        ${moveObject(media, binder.parameterRegister(REEL_ITEM_DESCRIPTOR))}
        if-eqz v$media, :piko_story_media
        iget-object v$media, v$media, $mediaField
        :piko_story_media
        ${moveObject(session, binder.parameterRegister(USER_SESSION_DESCRIPTOR))}
        invoke-static {v$container, v$media, v$session}, $DOWNLOAD_UTILS_DESCRIPTOR->addStoryDownloadButton($VIEW_DESCRIPTOR$OBJECT_DESCRIPTOR$USER_SESSION_DESCRIPTOR)V
        """.trimIndent(),
    )
}
