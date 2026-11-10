/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val VIEW_DESCRIPTOR = "Landroid/view/View;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"
private const val REEL_ITEM_DESCRIPTOR = "Lcom/instagram/model/reels/ReelItem;"
private const val STORY_DOWNLOAD_MEDIA = "$DOWNLOAD_DESCRIPTOR/StoryDownloadMedia;"

private object StoryToolbarBinderFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    filters = listOf(
        string("ReelViewerItemToolbarBinder.bindView")
    )
)

internal fun storyViewerItemList(): MethodReference {
    val candidates = StoryToolbarBinderFingerprint.originalMethod.parameterTypes.mapNotNull { type ->
        val methods = classDefByOrNull(type.toString())?.virtualMethods ?: return@mapNotNull null
        val indexedItem = methods.singleOrNull {
            it.parameterTypes == listOf(USER_SESSION_DESCRIPTOR, "I") && it.returnType == REEL_ITEM_DESCRIPTOR
        } ?: return@mapNotNull null
        val items = methods.singleOrNull {
            it.parameterTypes == listOf(USER_SESSION_DESCRIPTOR) && it.returnType == "Ljava/util/List;" &&
                AccessFlags.PUBLIC.isSet(it.accessFlags)
        } ?: return@mapNotNull null

        // Both accessors must read the same list to preserve the viewer's playback order.
        val listSource = items.implementation?.instructions
            ?.mapNotNull { it.getReference<MethodReference>() }
            ?.singleOrNull { it.returnType == "Ljava/util/List;" } ?: return@mapNotNull null
        val indexedCode = indexedItem.implementation ?: return@mapNotNull null
        items.takeIf { indexedCode.instructions.any { instruction -> instruction.getReference<MethodReference>() == listSource } }
    }
    return requireOne("story viewer parameter with an ordered item list", candidates)
}

val storyDownloadButtonPatch = patch {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(sharedExtensionPatch, settingsPatch)

    execute {
        hookStoryToolbarBinder()
        enableSettings("storyDownloadButton")
    }
}

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

    val mediaGetters = classDefBy(REEL_ITEM_DESCRIPTOR).methods.filter {
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

    val binder = StoryToolbarBinderFingerprint.method
    val items = storyViewerItemList()
    val reelType = items.definingClass
    Fingerprint(definingClass = STORY_DOWNLOAD_MEDIA, name = "nativeItems").method.addInstructions(
        0, """
        check-cast p0, $reelType
        invoke-virtual {p0, p1}, $items
        move-result-object p0
        return-object p0
        """.trimIndent(),
    )
    Fingerprint(definingClass = STORY_DOWNLOAD_MEDIA, name = "media").method.addInstructions(
        0, """
        check-cast p0, $REEL_ITEM_DESCRIPTOR
        iget-object p0, p0, $mediaField
        return-object p0
        """.trimIndent(),
    )
    val (container, media, session, reel) = binder.reserveFreeRegisters(0, 4, 15)
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
        ${moveObject(reel, binder.parameterRegister(reelType))}
        invoke-static {v$container, v$media, v$session, v$reel}, $DOWNLOAD_UTILS_DESCRIPTOR->addStoryDownloadButton($VIEW_DESCRIPTOR$OBJECT_DESCRIPTOR$USER_SESSION_DESCRIPTOR$OBJECT_DESCRIPTOR)V
        """.trimIndent(),
    )
}
