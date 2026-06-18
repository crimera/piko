/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.crimera.patches.instagram.misc.clone

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.asSequence
import app.morphe.util.findElementByAttributeValue
import app.morphe.util.findMutableMethodOf
import app.morphe.util.forEachChildElement
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element
import kotlin.collections.forEach

private const val ORIGINAL_PACKAGE_NAME = "com.instagram.android"

@Suppress("unused")
val clonePatch =
    resourcePatch(
        name = "Clone",
        description =
            "Changes the package name and the app name. " +
                "This allows you to install the patched app alongside the original Instagram app.\n" +
                "Caution: Do not select the official Morphe's \"Change package name\" universal patch.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val packageName by stringOption(
            key = "packageName",
            default = "com.instagram.android.piko",
            title = "Package name",
            description = "A new package name for the patched app.",
            required = true,
        ) {
            it!!.matches(Regex("^[a-z]\\w*(\\.[a-z]\\w*)+$"))
        }

        val appName by stringOption(
            key = "appName",
            default = "Piko Instagram",
            title = "App name",
            description = "A new app name (label). Entering \"Instagram\" will skip changing the app name.",
            required = true,
        )

        var bytecodePatchContext: BytecodePatchContext? = null

        dependsOn(
            // Expose the bytecode context to use it within a ResourcePatch
            bytecodePatch {
                execute {
                    bytecodePatchContext = this
                }
            },
        )

        execute {
            val newPackageName = packageName!!

            /** Pairs of the original authority and the renamed authority */
            val providerReplacements = mutableListOf<Pair<String, String>>()

            document("AndroidManifest.xml").use { document ->
                val manifest = document.documentElement

                // Change package name
                manifest.setAttribute("package", newPackageName)

                // Rename custom permissions
                val permissions = manifest.getElementsByTagName("permission")
                val usesPermissions = manifest.getElementsByTagName("uses-permission")

                permissions.asSequence().map { it as Element }.forEach {
                    val oldName = it.getAttribute("android:name")
                    if (oldName.startsWith('.')) {
                        return@forEach
                    }
                    val newName = oldName.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
                    it.setAttribute("android:name", newName)

                    // Rename a corresponding uses-permission as well if it exists
                    usesPermissions
                        .findElementByAttributeValue("android:name", oldName)
                        ?.setAttribute("android:name", newName)
                }

                // Rename provider authorities
                val providers = manifest.getElementsByTagName("provider").asSequence().map { it as Element }
                for (provider in providers) {
                    val oldAuthority = provider.getAttribute("android:authorities")
                    val newAuthority =
                        if (oldAuthority.startsWith("$ORIGINAL_PACKAGE_NAME.")) {
                            oldAuthority.replaceFirst(ORIGINAL_PACKAGE_NAME, newPackageName)
                        } else {
                            "${newPackageName}_$oldAuthority"
                        }

                    provider.setAttribute("android:authorities", newAuthority)
                    providerReplacements.add(oldAuthority to newAuthority)
                }
            }

            // Change app name
            if (!appName.isNullOrEmpty() && appName != "Instagram") {
                // The string "Instagram" is not localized and only exists in "values".
                // Resource names vary depending on the build.
                document("res/values/strings.xml").use { document ->
                    document.documentElement.forEachChildElement {
                        if (it.textContent == "Instagram") {
                            it.textContent = appName
                        }
                    }
                }
            }

            // Replace all instances of the string "com.instagram.android" in the bytecode to fix
            // the issue where video stories only play audio.
            // Also rename providers just in case.
            context(bytecodePatchContext!!) {
                transformStringReferences { string ->
                    if (string == ORIGINAL_PACKAGE_NAME) {
                        return@transformStringReferences newPackageName
                    }

                    val matchedReplacement = providerReplacements.find { string.contains(it.first) }

                    if (matchedReplacement != null) {
                        string.replaceFirst(matchedReplacement.first, matchedReplacement.second)
                    } else {
                        null
                    }
                }
            }
            bytecodePatchContext = null
        }
    }

// Taken from:
// https://github.com/MorpheApp/morphe-patches/blob/a8a566774ec8b99c02ce60fdc81ff5258c3b0caf/patches/src/main/kotlin/app/morphe/patches/shared/misc/gms/GmsCoreSupportPatch.kt#L91
// Slightly modified
context(patchContext: BytecodePatchContext)
private fun transformStringReferences(transform: (str: String) -> String?) {
    patchContext.getAllClassesWithStrings().forEach { classDef ->
        val mutableClass by lazy {
            patchContext.mutableClassDefBy(classDef)
        }

        classDef.methods.forEach { method ->
            val mutableMethod by lazy {
                mutableClass.findMutableMethodOf(method)
            }

            method.implementation?.instructions?.forEachIndexed { index, instruction ->
                val string =
                    instruction.getReference<StringReference>()?.string
                        ?: return@forEachIndexed

                // Apply transformation.
                val transformedString = transform(string) ?: return@forEachIndexed

                mutableMethod.replaceInstruction(
                    index,
                    BuilderInstruction21c(
                        Opcode.CONST_STRING,
                        (instruction as OneRegisterInstruction).registerA,
                        ImmutableStringReference(transformedString),
                    ),
                )
            }
        }
    }
}
