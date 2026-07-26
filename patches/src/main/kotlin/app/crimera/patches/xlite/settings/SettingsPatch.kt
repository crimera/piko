package app.crimera.patches.xlite.settings

import app.crimera.patches.xlite.misc.extension.xLiteExtensionPatch
import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.xlite.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal val xLiteSettingsPatch =
    bytecodePatch(default = false) {
        dependsOn(
            xLiteExtensionPatch,
            xLiteSettingsResourcePatch,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("xlite")

            val matches = ComposeSettingsBasicItemFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose settings row renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().let { match ->
                val originalMethod = match.originalMethod
                val iconType = originalMethod.parameterTypes[2].toString()
                val settingsIconField = resolveSettingsIconField(iconType)
                val rendererReference =
                    "${originalMethod.definingClass}->${originalMethod.name}(" +
                        originalMethod.parameterTypes.joinToString("") +
                        ")${originalMethod.returnType}"
                val titleRegister = match.method.findFreeRegister(0)
                val summaryRegister = match.method.findFreeRegister(0, titleRegister)
                val clickRegister =
                    match.method.findFreeRegister(0, listOf(titleRegister, summaryRegister))
                val iconRegister =
                    match.method.findFreeRegister(
                        0,
                        listOf(titleRegister, summaryRegister, clickRegister),
                    )

                match.method.addInstructionsWithLabels(
                    0,
                    """
                        invoke-static/range {p0 .. p0}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->isAdditionalResourcesTitle(Ljava/lang/String;)Z
                        move-result v$titleRegister
                        if-eqz v$titleRegister, :piko_xlite_settings_original
                        move-object/from16 v$titleRegister, p0
                        move-object/from16 v$summaryRegister, p1
                        move-object/from16 v$iconRegister, p2
                        move-object/from16 v$clickRegister, p3
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;
                        move-result-object p0
                        const/16 p1, 0x0
                        sget-object p2, $settingsIconField
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()Lkotlin/jvm/functions/Function0;
                        move-result-object p3
                        invoke-static/range {p0 .. p11}, $rendererReference
                        move-object/from16 p0, v$titleRegister
                        move-object/from16 p1, v$summaryRegister
                        move-object/from16 p2, v$iconRegister
                        move-object/from16 p3, v$clickRegister
                        :piko_xlite_settings_original
                        nop
                    """.trimIndent(),
                )
            }

            // sharedExtensionPatch finalizes after this patch and inserts Utils.setContext at
            // index zero, so registry loading always follows shared context initialization.
            xLiteInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static {}, $SETTINGS_REGISTRY_DESCRIPTOR->load()V",
            )
        }
    }

context(_: BytecodePatchContext)
private fun resolveSettingsIconField(iconType: String): FieldReference {
    val drawableId = getResourceId(ResourceType.DRAWABLE, "ic_vector_settings_stroke")
    val fingerprint =
        Fingerprint(
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(drawableId)),
        )
    val fields =
        fingerprint.matchAll().mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            match.method.instructions
                .drop(literalIndex + 1)
                .take(4)
                .firstOrNull {
                    it.opcode == Opcode.SPUT_OBJECT &&
                        it.getReference<FieldReference>()?.type == iconType
                }?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)

    if (fields.size != 1) {
        throw PatchException(
            "Expected one X-Lite settings icon field, found ${fields.size}: " +
                fields.joinToString(),
        )
    }
    return fields.single()
}
