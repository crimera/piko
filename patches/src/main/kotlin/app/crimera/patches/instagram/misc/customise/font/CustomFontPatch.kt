/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.font

import app.crimera.patches.instagram.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getMutableMethod
import com.android.tools.smali.dexlib2.iface.Method

private val GET_FONT_PARAMETERS = listOf("Landroid/content/Context;", "I")

@Suppress("unused")
val customFontPatch =
    bytecodePatch(
        name = "Custom font",
        description = "Adds an option to replace the app font with a font file from the device storage.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            // Whether there is a font to draw in, and which, is settled once when the app starts:
            // applying a font restarts the app, so nothing about it can change while it runs. Every
            // hook below then costs a single boolean read when the feature is switched off.
            SettingsStatusLoadFingerprint.method.addInstruction(
                0,
                "invoke-static {}, $LOAD_CUSTOM_FONT",
            )

            // The app resolves fonts through four independent paths, and text only changes
            // consistently when all four are covered.

            // 1. The typeface repository, used by the classic views. It is handed a descriptor of
            //    the font it is resolving, which is what tells the interface fonts apart from the
            //    creative fonts of the story editor, notes and profile bios.
            val typefaceRepository = TypefaceRepositoryLoadFingerprint.classDef.type
            TypefaceRepositoryLoadFingerprint.method.hookResolvedTypefaces()

            // 2. The IGDS font helper, used by IgTextViews, Bloks mounted text, spans and paints.
            //    It hands back the typeface it was given when the app's own font family is switched
            //    off, so every typeface it returns is hooked.
            IgdsFontHelperFingerprint.classDef.methods
                .filter { it.returnsTypeface() }
                .forEach { it.hookReturnedTypefaces() }

            // 3. Compose, which resolves fonts on its own and never reaches the repository.
            ComposePlatformTypefacesFingerprint.method.hookWrappedTypeface()

            // 4. The font resources the app declares of its own, for whatever loads one without
            //    going through Compose.
            ResourcesCompatFontFingerprint.classDef.methods
                .filter {
                    it.returnsTypeface() &&
                        it.parameterTypes.map(CharSequence::toString) == GET_FONT_PARAMETERS
                }
                .forEach { it.hookReturnedTypefaces() }

            // Fonts the user picks inside the app - story and reel stickers, note and profile bio
            // styles - are resolved by handing the repository itself to a resolver along with the
            // picked style. Those resolvers ask for fonts the interface uses as well, so they are
            // marked to keep the font they asked for. Collected first and edited after, so the
            // classes are not being rewritten while they are still being read.
            val contentFontResolvers = mutableListOf<Method>()
            classDefForEach { classDef ->
                classDef.methods.forEach { method ->
                    if (method.returnsTypeface() &&
                        method.parameterTypes.firstOrNull() == typefaceRepository
                    ) {
                        contentFontResolvers += method
                    }
                }
            }
            contentFontResolvers.forEach { it.getMutableMethod().markAsContentFontResolver() }

            // The story text styles of the older editor resolve their font from an enum instead,
            // and reach the repository without taking it as a parameter.
            LegacyStoryFontFingerprint.classDef.methods
                .filter { it.returnsTypeface() }
                .forEach { it.markAsContentFontResolver() }

            enableSettings("customFont")
        }
    }

/** Abstract and native methods have no returns to hook, so they are never of interest. */
private fun Method.returnsTypeface() = returnType == TYPEFACE_CLASS && implementation != null
