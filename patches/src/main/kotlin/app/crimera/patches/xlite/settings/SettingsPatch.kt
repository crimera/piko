package app.crimera.patches.xlite.settings

import app.crimera.patches.xlite.misc.extension.xLiteExtensionPatch
import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.xlite.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.findFreeRegister

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
                val rendererReference =
                    "${originalMethod.definingClass}->${originalMethod.name}(" +
                        originalMethod.parameterTypes.joinToString("") +
                        ")${originalMethod.returnType}"
                val titleRegister = match.method.findFreeRegister(0)
                val summaryRegister = match.method.findFreeRegister(0, titleRegister)
                val clickRegister =
                    match.method.findFreeRegister(0, listOf(titleRegister, summaryRegister))

                match.method.addInstructionsWithLabels(
                    0,
                    """
                        invoke-static/range {p0 .. p0}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->isAdditionalResourcesTitle(Ljava/lang/String;)Z
                        move-result v$titleRegister
                        if-eqz v$titleRegister, :piko_xlite_settings_original
                        move-object/from16 v$titleRegister, p0
                        move-object/from16 v$summaryRegister, p1
                        move-object/from16 v$clickRegister, p3
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;
                        move-result-object p0
                        const/16 p1, 0x0
                        invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()Lkotlin/jvm/functions/Function0;
                        move-result-object p3
                        invoke-static/range {p0 .. p11}, $rendererReference
                        move-object/from16 p0, v$titleRegister
                        move-object/from16 p1, v$summaryRegister
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
