/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.font

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/instagram/patches/font/ForceSystemFontPatch;"

/*
 * Changes Instagram's default theme font from prism_sans
 * to the Android system sans-serif font.
 */
private val forceSystemFontThemePatch =
    resourcePatch(
        name = "Force system font theme",
        description = "Internal dependency patch for Instagram's default theme font.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            document("res/values/styles.xml").use { stylesDocument ->

                val styles =
                    stylesDocument.getElementsByTagName("style")

                var fontFamilyItem: Element? = null

                for (styleIndex in 0 until styles.length) {

                    val style =
                        styles.item(styleIndex) as? Element
                            ?: continue

                    if (
                        style.getAttribute("name") !=
                        "Base.Theme.Instagram"
                    ) {
                        continue
                    }

                    val items =
                        style.getElementsByTagName("item")

                    for (itemIndex in 0 until items.length) {

                        val item =
                            items.item(itemIndex) as? Element
                                ?: continue

                        if (
                            item.getAttribute("name") !=
                            "android:fontFamily"
                        ) {
                            continue
                        }

                        if (fontFamilyItem != null) {
                            throw PatchException(
                                "Found multiple Instagram theme font declarations."
                            )
                        }

                        fontFamilyItem = item
                    }
                }

                val item =
                    fontFamilyItem
                        ?: throw PatchException(
                            "Could not find Instagram's default theme font declaration."
                        )

                if (
                    item.textContent.trim() !=
                    "@font/prism_sans"
                ) {
                    throw PatchException(
                        "Instagram's default theme font has an unexpected value."
                    )
                }

                item.textContent = "sans-serif"
            }
        }
    }

/*
 * Forces Instagram's custom fonts to use
 * Android's system Typeface.
 */
@Suppress("unused")
val forceSystemFontPatch =
    bytecodePatch(
        name = "Force system font",
        description = "Renders Instagram UI text using the device system font.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(
            forceSystemFontThemePatch,
            sharedExtensionPatch,
        )

        execute {

            /*
             * Hook AndroidX ResourcesCompat.getFont(...)
             */
            ResourcesCompatGetFontFingerprint.method.addInstructions(
                0,
                """
                    move-object/from16 v0, p0
                    move/from16 v1, p3
                    move/from16 v2, p4

                    invoke-static {v0, v1, v2},
                        $EXTENSION_CLASS->getSystemTypeface(
                            Landroid/content/Context;
                            II
                        )Landroid/graphics/Typeface;

                    move-result-object v0

                    if-eqz v0, :original

                    return-object v0

                    :original
                    nop
                """.trimIndent(),
            )

            /*
             * Hook Instagram's React Native
             * variable font registration.
             */
            val reactNativeMethod =
                ReactNativeFontRegistrationFingerprint.method

            val instructions =
                reactNativeMethod
                    .implementation
                    ?.instructions
                    ?: throw PatchException(
                        "React Native font registration has no implementation."
                    )

            val registrationStringIndex =
                ReactNativeFontRegistrationFingerprint
                    .stringMatches
                    .single()
                    .index

            val searchStart =
                maxOf(0, registrationStringIndex - 12)

            /*
             * Find Typeface factory call.
             */
            val typefaceFactoryIndex =
                (searchStart until registrationStringIndex)
                    .lastOrNull { index ->

                        val instruction =
                            instructions[index]

                        val reference =
                            (instruction as? ReferenceInstruction)
                                ?.reference as? MethodReference

                        instruction.opcode ==
                            Opcode.INVOKE_VIRTUAL &&

                            instruction is FiveRegisterInstruction &&

                            reference?.returnType ==
                                "Landroid/graphics/Typeface;" &&

                            reference.parameterTypes.size == 1 &&

                            reference.parameterTypes[0].toString() ==
                                "Landroid/content/Context;"
                    }
                    ?: throw PatchException(
                        "Could not find React Native's variable-font factory call."
                    )

            val typefaceReference =
                (
                    instructions[typefaceFactoryIndex]
                        as ReferenceInstruction
                    ).reference as MethodReference

            /*
             * Find the variable font weight setter.
             */
            val weightSetterIndex =
                (
                    maxOf(
                        searchStart,
                        typefaceFactoryIndex - 4,
                    ) until typefaceFactoryIndex
                )
                    .lastOrNull { index ->

                        val instruction =
                            instructions[index]

                        val reference =
                            (instruction as? ReferenceInstruction)
                                ?.reference as? MethodReference

                        instruction.opcode ==
                            Opcode.INVOKE_VIRTUAL &&

                            instruction is FiveRegisterInstruction &&

                            reference?.definingClass ==
                                typefaceReference.definingClass &&

                            reference.returnType == "V" &&

                            reference.parameterTypes.size == 1 &&

                            reference.parameterTypes[0].toString() == "I"
                    }
                    ?: throw PatchException(
                        "Could not find React Native's variable-font weight setter."
                    )

            /*
             * The Typeface factory should be
             * immediately followed by move-result-object.
             */
            val resultInstruction =
                instructions.getOrNull(
                    typefaceFactoryIndex + 1
                )

            if (
                resultInstruction?.opcode !=
                    Opcode.MOVE_RESULT_OBJECT ||

                resultInstruction !is OneRegisterInstruction
            ) {
                throw PatchException(
                    "React Native's variable-font result has an unexpected shape."
                )
            }

            val weightInstruction =
                instructions[weightSetterIndex]
                    as FiveRegisterInstruction

            val weightRegister =
                weightInstruction.registerD

            val resultRegister =
                resultInstruction.registerA

            /*
             * Replace the Typeface returned by Instagram
             * with our system Typeface.
             */
            reactNativeMethod.addInstructions(
                typefaceFactoryIndex + 2,
                """
                    invoke-static {v$weightRegister},
                        $EXTENSION_CLASS->getSystemTypefaceForWeight(I)Landroid/graphics/Typeface;

                    move-result-object v$resultRegister
                """.trimIndent(),
            )
        }
    }