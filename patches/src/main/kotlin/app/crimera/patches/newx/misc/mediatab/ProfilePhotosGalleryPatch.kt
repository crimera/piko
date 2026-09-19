package app.crimera.patches.newx.misc.mediatab

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.inlineactions.newXThumbnailCachePatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val URT_UI_SCOPE = "Lcom/x/urt/ui/"
private const val OBJECT_LIST = "Lkotlinx/collections/immutable/b;"
private const val TIMELINE_TYPE = "Lcom/x/models/timelines/u;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val PADDING_VALUES = "Landroidx/compose/foundation/layout/y2;"
private const val PADDING_TOP = "$PADDING_VALUES->d()F"
private const val PADDING_BOTTOM = "$PADDING_VALUES->a()F"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val PHOTOS_ENUM = "Lcom/x/models/timelines/u;->USER_PROFILE_PHOTOS:Lcom/x/models/timelines/u;"
private const val ANDROID_VIEW =
    "Landroidx/compose/ui/viewinterop/j;->a(" +
        "Lkotlin/jvm/functions/Function1;" +
        "Landroidx/compose/ui/Modifier;" +
        "Lkotlin/jvm/functions/Function1;" +
        "Landroidx/compose/runtime/Composer;II)V"
private const val GALLERY_EXTENSION = "Lapp/morphe/extension/newx/misc/ProfilePhotosGallery;"

private fun isNewXTimelineBody(
    method: com.android.tools.smali.dexlib2.iface.Method,
    classType: String,
): Boolean {
    if (!AccessFlags.STATIC.isSet(method.accessFlags)) return false
    if (!classType.startsWith(URT_UI_SCOPE)) return false

    val parameters = method.parameterTypes.map(CharSequence::toString)
    if (parameters.count { it == OBJECT_LIST } != 1) return false
    if (parameters.count { it == TIMELINE_TYPE } != 1) return false
    if (parameters.count { it == MODIFIER } != 1) return false
    if (parameters.count { it == COMPOSER } != 1) return false

    return method.implementation?.instructions?.any { instruction ->
        instruction.opcode == Opcode.NEW_INSTANCE &&
            (instruction as? ReferenceInstruction)?.reference.toString() == "Lcom/x/urt/ui/y;"
    } == true
}

/** The URT timeline body. The owner is `a0` in production and shifts in alpha builds. */
private object NewXTimelineBodyFingerprint : Fingerprint(
    definingClass = URT_UI_SCOPE,
    name = "e",
    returnType = "V",
    custom = { method, classDef -> isNewXTimelineBody(method, classDef.type) },
)

private fun parameterRegister(method: com.android.tools.smali.dexlib2.iface.Method, index: Int): Int {
    val implementation = method.implementation
        ?: throw PatchException("Photos timeline renderer has no implementation: $method")
    return implementation.registerCount - method.parameterTypes.size + index
}

private fun galleryInstructions(
    listRegister: Int,
    timelineTypeRegister: Int,
    callbackRegister: Int,
    paddingValuesRegister: Int,
    modifierRegister: Int,
    composerRegister: Int,
): String =
    """
        sget-object v0, $PHOTOS_ENUM
        move-object/from16 v1, v$timelineTypeRegister
        if-ne v1, v0, :piko_newx_photos_original
        invoke-static {}, $GALLERY_EXTENSION->isEnabled()Z
        move-result v0
        if-eqz v0, :piko_newx_photos_original
        move-object/from16 v0, v$listRegister
        move-object/from16 v1, v$callbackRegister
        move-object/from16 v6, v$paddingValuesRegister
        invoke-interface {v6}, $PADDING_TOP
        move-result v2
        invoke-interface {v6}, $PADDING_BOTTOM
        move-result v3
        invoke-static/range {v0 .. v3}, $GALLERY_EXTENSION->createFactory(Ljava/util/List;Ljava/lang/Object;FF)$FUNCTION1
        move-result-object v0
        move-object v7, v0
        move-object/from16 v0, v$listRegister
        move-object/from16 v1, v$callbackRegister
        move-object/from16 v6, v$paddingValuesRegister
        invoke-interface {v6}, $PADDING_TOP
        move-result v2
        invoke-interface {v6}, $PADDING_BOTTOM
        move-result v3
        invoke-static/range {v0 .. v3}, $GALLERY_EXTENSION->createUpdater(Ljava/util/List;Ljava/lang/Object;FF)$FUNCTION1
        move-result-object v2
        move-object v0, v7
        move-object/from16 v1, v$modifierRegister
        move-object/from16 v3, v$composerRegister
        const/4 v4, 0
        const/4 v5, 0
        invoke-static/range {v0 .. v5}, $ANDROID_VIEW
        return-void
        :piko_newx_photos_original
    """.trimIndent()

@Suppress("unused")
val newXProfilePhotosGalleryPatch =
    bytecodePatch(
        name = "NewX: Gallery profile Photos tab",
        description = "Replaces the profile Photos timeline with a three-column media gallery.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch, newXDefaultMediaTabPatch, newXThumbnailCachePatch)

        val galleryToggle =
            newXToggle(
                id = "newx.post_actions_media.gallery_profile_photos",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_gallery_profile_photos"),
                order = 105,
                defaultValue = true,
            )

        execute {
            val match = requireExactlyOne(
                label = "NewX profile Photos timeline body",
                candidates = NewXTimelineBodyFingerprint.scopedMatchAll(),
            )
            val originalMethod = match.method
            val parameters = originalMethod.parameterTypes.map(CharSequence::toString)
            val listIndex = parameters.indexOf(OBJECT_LIST)
            val timelineTypeIndex = parameters.indexOf(TIMELINE_TYPE)
            val function1Index = parameters.indexOf(FUNCTION1)
            val paddingValuesIndex = parameters.indexOf(PADDING_VALUES)
            val modifierIndex = parameters.indexOf(MODIFIER)
            val composerIndex = parameters.indexOf(COMPOSER)
            if (
                listIndex < 0 ||
                timelineTypeIndex < 0 ||
                function1Index < 0 ||
                paddingValuesIndex < 0 ||
                modifierIndex < 0 ||
                composerIndex < 0
            ) {
                throw PatchException(
                    "Photos timeline body lost a required parameter: $parameters",
                )
            }

            val method = originalMethod.cloneMutable(
                additionalRegisters = 8,
            ).also { expanded ->
                match.classDef.methods.remove(originalMethod)
                match.classDef.methods.add(expanded)
            }

            method.addInstructions(
                0,
                galleryInstructions(
                    listRegister = parameterRegister(method, listIndex),
                    timelineTypeRegister = parameterRegister(method, timelineTypeIndex),
                    callbackRegister = parameterRegister(method, function1Index),
                    paddingValuesRegister = parameterRegister(method, paddingValuesIndex),
                    modifierRegister = parameterRegister(method, modifierIndex),
                    composerRegister = parameterRegister(method, composerIndex),
                ),
            )
        }
    }
