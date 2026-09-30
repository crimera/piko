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

            // The font is substituted where a typeface is assigned - on the app's text views, on
            // paints and in platform spans - however it was resolved. Three resolvers are hooked as
            // well: the repository, and Compose and React Native, which resolve fonts on their own.

            // The typeface repository. Its descriptor tells interface fonts apart from the creative
            // fonts of the story editor, notes and bios, which are recorded and kept.
            val typefaceRepository = TypefaceRepositoryLoadFingerprint.classDef.type
            TypefaceRepositoryLoadFingerprint.method.hookResolvedTypefaces()

            // Compose, which resolves fonts on its own and never reaches the repository.
            ComposePlatformTypefacesFingerprint.method.hookWrappedTypeface()

            // React Native's "Optimistic VF App Lite" variable font, resolved on its own.
            val registrationIndex = ReactNativeFontRegistrationFingerprint.stringMatches.single().index
            ReactNativeFontRegistrationFingerprint.method.hookReactNativeFontRegistration(registrationIndex)

            // Collected in one pass and edited after, so classes are not rewritten while read:
            // resolvers of fonts the user picks inside the app (they take the repository as their
            // first parameter), the app's own text views, Paint.setTypeface calls, creations of
            // platform spans that set their font inside the framework, and the plain text widgets
            // piko's own code creates.
            val contentFontResolvers = mutableListOf<Method>()
            val textViewClasses = mutableListOf<String>()
            val paintTypefaceCallers = mutableListOf<Method>()
            val platformSpanCreators = mutableListOf<Method>()
            val pikoWidgetCreators = mutableListOf<Method>()
            classDefForEach { classDef ->
                // The font classes call CustomFont and extend the widgets, so must not be hooked.
                if (classDef.type.startsWith(FONT_EXTENSION_PACKAGE)) return@classDefForEach
                val isPikoClass = classDef.type.startsWith(EXTENSION_PACKAGE)
                if (classDef.superclass in PLATFORM_TEXT_WIDGETS) {
                    textViewClasses += classDef.type
                }
                classDef.methods.forEach { method ->
                    if (method.returnsTypeface() &&
                        method.parameterTypes.firstOrNull() == typefaceRepository
                    ) {
                        contentFontResolvers += method
                    }
                    var callsPaintTypeface = false
                    var createsPlatformSpan = false
                    var createsPikoWidget = false
                    method.implementation?.instructions?.forEach {
                        callsPaintTypeface = callsPaintTypeface || it.isPaintTypefaceCall()
                        createsPlatformSpan = createsPlatformSpan || it.createsOneOf(PLATFORM_TYPEFACE_SPANS)
                        createsPikoWidget = createsPikoWidget || (isPikoClass && it.createsOneOf(PIKO_TEXT_WIDGETS))
                    }
                    if (callsPaintTypeface) paintTypefaceCallers += method
                    if (createsPlatformSpan) platformSpanCreators += method
                    if (createsPikoWidget) pikoWidgetCreators += method
                }
            }
            contentFontResolvers.forEach { it.getMutableMethod().markAsContentFontResolver() }

            // The story text styles of the older editor resolve their font from an enum instead,
            // and reach the repository without taking it as a parameter.
            LegacyStoryFontFingerprint.classDef.methods
                .filter { it.returnsTypeface() }
                .forEach { it.markAsContentFontResolver() }

            textViewClasses.forEach { mutableClassDefBy(it).overrideTypefaceAssignment() }

            // Creative text drawn by the content font resolvers themselves keeps its font.
            val contentFontClasses =
                contentFontResolvers.map { it.definingClass }.toSet() +
                    LegacyStoryFontFingerprint.classDef.type
            paintTypefaceCallers
                .filter { it.definingClass !in contentFontClasses }
                .forEach { it.getMutableMethod().hookPaintTypefaceCalls() }
            platformSpanCreators
                .filter { it.definingClass !in contentFontClasses }
                .forEach { it.getMutableMethod().replaceCreations(PLATFORM_TYPEFACE_SPANS) }
            pikoWidgetCreators.forEach { it.getMutableMethod().replaceCreations(PIKO_TEXT_WIDGETS) }

            enableSettings("customFont")
        }
    }

private const val EXTENSION_PACKAGE = "Lapp/morphe/extension/"

/** Abstract and native methods have no returns to hook, so they are never of interest. */
private fun Method.returnsTypeface() = returnType == TYPEFACE_CLASS && implementation != null
