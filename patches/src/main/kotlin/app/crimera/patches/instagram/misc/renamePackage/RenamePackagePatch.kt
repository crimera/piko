package app.crimera.patches.instagram.misc.renamePackage

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val ORIGINAL_PACKAGE = "com.instagram.android"
private const val ORIGINAL_NAMESPACE_PREFIX = "com.instagram"
private val PACKAGE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
private val NAMESPACE_WORD_REGEX =
    Regex("(?<![A-Za-z0-9_])" + Regex.escape(ORIGINAL_NAMESPACE_PREFIX) + "(?![A-Za-z0-9_])")

private const val MAIN_ACTION = "android.intent.action.MAIN"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

private val SKIP_CLASS_REFERENCE_ATTRS: Set<Pair<String, String>> =
    setOf(
        "application" to "android:name",
        "application" to "android:backupAgent",
        "application" to "android:appComponentFactory",
        "application" to "android:zygotePreloadName",
        "activity" to "android:name",
        "activity" to "android:parentActivityName",
        "service" to "android:name",
        "receiver" to "android:name",
        "provider" to "android:name",
        "instrumentation" to "android:name",
        "activity-alias" to "android:targetActivity",
        "meta-data" to "android:value",
    )

private var newPackageName: String? = null
private var newAppLabel: String? = null
private var knownInstagramClassNames: Set<String> = emptySet()

/**
 * Internal companion. The main bytecode patch declares this dependsOn, so it
 * runs alongside. The actual work happens; finalize so it can read the package
 * and label set during execute. Leaves the manifest untouched if those values
 * were never set, which means the user did not enable Rename package.
 */

internal val renamePackageManifestPatch =
    resourcePatch(
        description = "Manifest edits for the Rename package patch.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        finalize {
            val newPkg = newPackageName ?: return@finalize
            val newLabel = newAppLabel ?: return@finalize
            val newNamespacePrefix = newPkg.substringBeforeLast(".")

            document("AndroidManifest.xml").use { document ->
                val manifest = document.documentElement
                    ?: throw IllegalStateException(
                        "Rename package: <manifest> root not found.",
                    )

                manifest.setAttribute("package", newPkg)
                rewritePackageReferences(manifest, newNamespacePrefix)
                overrideLauncherLabels(manifest, newLabel)
            }
        }
    }

/**
 * Main user-facing patch. Carries the packageName and appLabel options, rewrites
 * matching string literals in dex, and triggers the manifest companion to run.
 */
@Suppress("unused")
val renamePackagePatch =
    bytecodePatch(
        name = "Rename package",
        description = "Renames the patched app's package and label so it installs alongside the " +
            "original Instagram instead of replacing it. Rewrites the manifest, launcher icon " +
            "labels, AND critical dex strings (custom intent actions, content provider URIs, " +
            "notification channel ids, FB-permissions) so push, RTC and ContentProvider-backed " +
            "features keep working. A new package means a fresh login, no Play Store updates, " +
            "and Play Protect may warn about the clone on install.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(renamePackageManifestPatch)

        val packageName by stringOption(
            key = "packageName",
            default = "com.pikogram.android",
            title = "Package name",
            description = "New package name for the cloned app. Must be a valid Java package " +
                "(e.g. com.pikogram.android). Cannot be com.instagram.android.",
            required = true,
        ) { value -> value != null && PACKAGE_NAME_REGEX.matches(value) && value != ORIGINAL_PACKAGE }

        val appLabel by stringOption(
            key = "appLabel",
            default = "Pikogram",
            title = "App label",
            description = "Name shown on the home screen and in app settings.",
            required = true,
        ) { value -> !value.isNullOrBlank() }

        execute {
            val newPkg = packageName!!
            val newLabel = appLabel!!.trim()
            val newNamespacePrefix = newPkg.substringBeforeLast(".")

            newPackageName = newPkg
            newAppLabel = newLabel

            val newReplacement = Regex.escapeReplacement(newNamespacePrefix)

            val instagramClassNames = mutableSetOf<String>()
            classDefForEach { classDef ->
                val type = classDef.type
                if (type.startsWith("Lcom/instagram/") && type.endsWith(";")) {
                    instagramClassNames +=
                        type.substring(1, type.length - 1).replace('/', '.')
                }
            }
            knownInstagramClassNames = instagramClassNames.toSet()

            classDefForEach { classDef ->
                val hasMatch =
                    classDef.methods.any { method ->
                        method.implementation?.instructions?.any { instruction ->
                            instruction.isConstString() &&
                                instruction.constStringValue()?.let {
                                    NAMESPACE_WORD_REGEX.containsMatchIn(it) &&
                                        it !in instagramClassNames
                                } == true
                        } == true
                    }
                if (!hasMatch) return@classDefForEach

                mutableClassDefBy(classDef).methods.forEach { method ->
                    val implementation = method.implementation ?: return@forEach
                    implementation.instructions.toList().forEachIndexed { index, instruction ->
                        if (!instruction.isConstString()) return@forEachIndexed
                        val original = instruction.constStringValue() ?: return@forEachIndexed
                        if (!NAMESPACE_WORD_REGEX.containsMatchIn(original)) return@forEachIndexed

                        if (original in instagramClassNames) return@forEachIndexed

                        val rewritten = NAMESPACE_WORD_REGEX.replace(original, newReplacement)
                        val register = (instruction as OneRegisterInstruction).registerA

                        method.replaceInstruction(
                            index,
                            "const-string v$register, \"${rewritten.toSmaliEscaped()}\"",
                        )
                    }
                }
            }
        }
    }

/** True if this instruction is a const-string or const-string/jumbo. */
private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.isConstString(): Boolean =
    opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO

/** The string this const-string instruction loads, or null if it is not one. */
private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.constStringValue(): String? =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

/**
 * Walks the element tree and rewrites every attribute value matching the
 * com.instagram word-boundary regex. Skips entries in SKIP_CLASS_REFERENCE_ATTRS,
 * which hold Java class names that must keep pointing at the original
 * com.instagram.* classes in dex. Also skips activity-alias android:name when
 * the value is the FQCN of a real class in the dex — dex code references those
 * aliases via Class.forName / CONST_CLASS and would resolve to the original
 * com.instagram.* path regardless of what we put in the manifest.
 */
private fun rewritePackageReferences(element: Element, newNamespacePrefix: String) {
    val replacement = Regex.escapeReplacement(newNamespacePrefix)
    val tagName = element.tagName

    val attributes = element.attributes
    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        val attrName = attribute.nodeName
        if ((tagName to attrName) in SKIP_CLASS_REFERENCE_ATTRS) continue
        val original = attribute.nodeValue ?: continue
        if (tagName == "activity-alias"
            && attrName == "android:name"
            && original in knownInstagramClassNames
        ) continue
        val rewritten = NAMESPACE_WORD_REGEX.replace(original, replacement)
        if (rewritten != original) attribute.nodeValue = rewritten
    }

    val children = element.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType == Node.ELEMENT_NODE) {
            rewritePackageReferences(child as Element, newNamespacePrefix)
        }
    }
}

/**
 * Sets android:label on every activity and activity-alias whose intent filter
 * declares both MAIN and LAUNCHER. The home-screen icon reads this, separate
 * from the application-level label.
 */
private fun overrideLauncherLabels(manifest: Element, newLabel: String) {
    fun visit(node: Node) {
        if (node is Element
            && (node.tagName == "activity" || node.tagName == "activity-alias")
            && hasLauncherIntentFilter(node)) {
            node.setAttribute("android:label", newLabel)
        }
        val children = node.childNodes
        for (i in 0 until children.length) visit(children.item(i))
    }
    visit(manifest)
}

/** True if the element has an intent-filter with both action MAIN and category LAUNCHER. */
private fun hasLauncherIntentFilter(element: Element): Boolean {
    val filters = element.getElementsByTagName("intent-filter")
    return (0 until filters.length).any { i ->
        val filter = filters.item(i) as Element
        filter.hasChildWithAttribute("action", MAIN_ACTION) &&
            filter.hasChildWithAttribute("category", LAUNCHER_CATEGORY)
    }
}

private fun Element.hasChildWithAttribute(tag: String, expectedName: String): Boolean {
    val children = getElementsByTagName(tag)
    return (0 until children.length).any { i ->
        (children.item(i) as Element).getAttribute("android:name") == expectedName
    }
}


/**
 * Escapes characters that break a smali double-quoted string literal: backslash,
 * double quote, the standard whitespace controls (\n \r \t \b), and any other
 * control character via \uXXXX.
 */
private fun String.toSmaliEscaped(): String {
    val out = StringBuilder(length + 8)
    for (c in this) {
        when {
            c == '\\' -> out.append("\\\\")
            c == '"' -> out.append("\\\"")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c == '\b' -> out.append("\\b")
            c.code < 0x20 || c.code == 0x7F ->
                out.append("\\u%04x".format(c.code))
            else -> out.append(c)
        }
    }
    return out.toString()
}
