/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

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

// The IG NAMESPACE prefix — broader than the package. We rename `com.instagram.*`
// wholesale (not just `com.instagram.android.*`) so sibling sub-namespaces also
// migrate to the clone's prefix:
//
//   com.instagram.fileprovider                 ← classic FileProvider authority
//   com.instagram.contentprovider.*            ← 4 ContentProvider authorities
//   com.instagram.barcelona.* (Threads)        ← 2 more authorities
//   com.instagram.liteprovider.*               ← Lite Provider authorities
//   com.instagram.quicksnap.seenstate
//   com.instagram.foabackuptoken.*
//   com.instagram.creation.drafts.*
//   com.instagram.growth.xav.*
//
// These are SYSTEM-WIDE-UNIQUE identifiers — Android allows exactly one app to
// declare each. v3.5.5 left them alone (only the `.android.*` prefix was
// renamed), which meant the clone OWNED these authorities and PREVENTED the
// original Instagram from installing alongside it. Renaming them under the
// clone's prefix unblocks side-by-side install.
private const val ORIGINAL_NAMESPACE_PREFIX = "com.instagram"

// Require ≥ 3 segments so the derived namespace prefix (last segment stripped)
// has ≥ 2. With only 2 segments (`com.foo`) the prefix would collapse to `com`
// and rewrites like `com.instagram.X` → `com.X` would collide with countless
// other apps. Default `com.pikogram.android` has 3; the user's package must too.
private val PACKAGE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}$")

// Word-boundary regex catching `com.instagram` whenever it appears as a complete
// identifier "word" — at start/end of string or surrounded by non-identifier
// chars (typically `.` for sub-segments). Used for BOTH manifest attribute
// values AND dex string literals so the manifest and bytecode stay consistent.
//
// Behaviour vs. the v3.5.5 narrow regex:
//   • rewrites: com.instagram.android.*, com.instagram.direct.*, com.instagram.fileprovider, ...
//   • rewrites: cct.com.instagram.android (Chrome Custom Tab redirect host)
//   • leaves alone: com.instagram2 (identifier-char boundary fails)
//   • cosmetic loss: Play Store / market:// URLs with id=com.instagram.android get
//     rewritten to id=com.<new>.android (URL becomes wrong) — already broken in
//     v3.5.4 onwards because of the bare-string unspare.
private val NAMESPACE_WORD_REGEX =
    Regex("(?<![A-Za-z0-9_])" + Regex.escape(ORIGINAL_NAMESPACE_PREFIX) + "(?![A-Za-z0-9_])")

private const val MAIN_ACTION = "android.intent.action.MAIN"
private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

// (tagName, attributeName) pairs whose values are **Java class references**,
// not identifiers. Renaming them produces ClassNotFoundException because the
// real Java class in the dex stays at the original `com.instagram.X.Y` path —
// we only rename string literals, never type descriptors.
//
// Verified in v3.5.7 on-device logcat: rewriting `<application android:name>`
// to `com.opengram.app.InstagramAppShell` made Android's class loader fail
// before `Application.onCreate`, killing the process at startup.
//
// `<activity-alias android:name>` is intentionally NOT in this set — it's a
// logical component identifier (no real class), and it MUST be renamed in
// sync with the dex strings that reference it (e.g. ComponentName look-ups).
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
        // <meta-data android:value="…"> is often a class reference passed to
        // a library (e.g. Google Cast's OPTIONS_PROVIDER_CLASS_NAME points at
        // "com.instagram.casting.google.AirwaveCastOptionsProvider"). Skip
        // for safety — the few cases where the value is a plain identifier we
        // could rename are not load-bearing for the clone.
        "meta-data" to "android:value",
    )

// State shared between this file's two patches. The bytecodePatch sets these
// during execute; the resourcePatch reads them during finalize (which runs
// strictly after all execute phases). Null means "Rename package wasn't
// enabled" — the manifest patch is a no-op in that case even though it was
// pulled in via the dependency edge.
private var newPackageName: String? = null
private var newAppLabel: String? = null

/**
 * Internal manifest-side companion. Has no `name`, so it doesn't show up
 * as a user-selectable patch; it's pulled in by the bytecode patch's
 * `dependsOn`. Runs in `finalize {}` so its work happens AFTER the
 * bytecode patch has written the shared state.
 */
internal val renamePackageManifestPatch =
    resourcePatch(
        description = "Manifest edits for the Rename package patch (internal).",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        finalize {
            val newPkg = newPackageName ?: return@finalize
            val newLabel = newAppLabel ?: return@finalize
            // Derived prefix: strip the last segment of the user's package.
            //   com.opengram.android  → com.opengram
            //   com.user.x.android    → com.user.x
            // We rename every `com.instagram.X` to `<this prefix>.X` so sibling
            // sub-namespaces (.direct, .barcelona, .fileprovider, etc.) move
            // under the clone too.
            val newNamespacePrefix = newPkg.substringBeforeLast(".")

            document("AndroidManifest.xml").use { document ->
                val manifest = document.documentElement
                    ?: throw IllegalStateException(
                        "Rename package: <manifest> root not found.",
                    )

                // 1. Authoritative package name at install time.
                manifest.setAttribute("package", newPkg)

                // 2. Rewrite every word-boundary com.instagram reference. This
                //    catches ALL IG-owned namespaces: the app package
                //    (com.instagram.android.*), permissions, FileProvider
                //    authorities, channel ids, deeplink hosts, AND sibling
                //    sub-namespaces like com.instagram.fileprovider,
                //    com.instagram.barcelona.*, com.instagram.contentprovider.*,
                //    etc. The sibling rename is what unblocks side-by-side
                //    install with the Play Store IG.
                rewritePackageReferences(manifest, newNamespacePrefix)

                // 3. DELIBERATELY DO NOT touch <application android:label>.
                //    Setting it to a literal string makes `ApplicationInfo.labelRes`
                //    drop to 0 (the resource id slot is only populated when the label
                //    is a `@string/...` reference). IG's WaitingForStringsActivity
                //    reads that field directly and passes it to getString(0), which
                //    throws Resources$NotFoundException: String resource ID #0x0 →
                //    process killed on first launch. Verified in v3.5.2 logcat.
                //    Cosmetic cost: the App Info screen will keep saying "Instagram"
                //    (it reads the application label). Acceptable because the
                //    LAUNCHER ICON below is what the user actually sees.

                // 4. Launcher-entry labels. The home-screen icon prefers the
                //    activity (or activity-alias) label over the application
                //    label; setting these to a literal string is safe because
                //    Android stores it in `ActivityInfo.nonLocalizedLabel` and
                //    doesn't ask any activity to call `getString` on a 0 id.
                //    IG declares TWO launcher aliases — InternalLauncher (disabled
                //    by default) and MainTabActivity — both get the new label via
                //    the intent-filter search.
                overrideLauncherLabels(manifest, newLabel)
            }

            // 5. Override the value of the *string resource* that IG's
            //    `<application android:label>` already references, instead of
            //    rewriting the manifest's android:label attribute itself.
            //    Why: if we set android:label to a literal, `labelRes = 0` and
            //    `getString(0)` crashes WaitingForStringsActivity (lesson from
            //    v3.5.1). Editing the string the manifest *points at* keeps
            //    labelRes valid (it still points to the same resource id) and
            //    just changes what that id resolves to. Result: the install
            //    dialog, App Info screen, AND any other application-label
            //    reader all render the user's chosen label.
            //
            //    IG ships with stripped resource names. In v426.0.0.37.68 the
            //    label string is resource id 0x7f13000a; the resource decoder
            //    reconstructs the name as "string_0x7f13000a" (verified by
            //    aapt2 dump). We also try "app_name" as a fallback in case
            //    Morphe's decoder ever exposes the symbolic name. If the
            //    string isn't found by any candidate name, the launcher-alias
            //    label override above still drives the home-screen icon, so
            //    we degrade gracefully.
            val labelStringCandidates = listOf("string_0x7f13000a", "app_name")
            runCatching {
                document("res/values/strings.xml").use { stringsDoc ->
                    val strings = stringsDoc.getElementsByTagName("string")
                    candidateLoop@ for (candidate in labelStringCandidates) {
                        for (i in 0 until strings.length) {
                            val element = strings.item(i) as Element
                            if (element.getAttribute("name") == candidate) {
                                element.textContent = newLabel
                                break@candidateLoop
                            }
                        }
                    }
                }
            }
        }
    }

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
                "with AT LEAST 3 segments (e.g. com.pikogram.android). The patch derives a " +
                "namespace prefix by dropping the last segment — that prefix replaces " +
                "com.instagram everywhere, so a 2-segment package would collapse the IG " +
                "sub-namespaces to single-word identifiers and collide with other apps. " +
                "Cannot be com.instagram.android.",
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

            // Hand state off to the manifest companion's finalize block.
            newPackageName = newPkg
            newAppLabel = newLabel

            // Rewrite `const-string` / `const-string/jumbo` literals containing
            // `com.instagram` as an identifier word. The regex (NAMESPACE_WORD_REGEX)
            // is applied anywhere in the string — not just as a prefix — so it
            // catches every IG-owned identifier the manifest also renames:
            //
            //   • app-package strings: com.instagram.android.RTC_LEAVE_CALL_ACTION,
            //                          com.instagram.android.channel, etc.
            //   • content URIs:        content://com.instagram.android.baselcontext/
            //                          content://com.instagram.fileprovider/foo
            //   • sibling namespaces:  com.instagram.barcelona.contentprovider.X,
            //                          com.instagram.direct.permission.Y, ...
            //   • mid-string forms:    cct.com.instagram.android (Chrome Custom Tab redirect)
            //
            // Word-boundary lookarounds keep `com.instagram2` and similar from
            // matching. EXTERNAL URLs (`play.google.com/store/...?id=com.instagram.android`,
            // `market://details?id=com.instagram.android`, `android-app://com.instagram.android`)
            // DO get rewritten — already true since v3.5.4's bare-string unspare; the URL
            // becomes wrong but no crash. Cosmetic.
            val newReplacement = Regex.escapeReplacement(newNamespacePrefix)

            // Pre-pass: collect every Java class name in dex under com.instagram.* —
            // these are reflection targets (Class.forName, ComponentName, etc.) and
            // must NOT be rewritten. Verified in v3.5.8 on-device logcat:
            //   InstagramAppShell.onCreate calls
            //   Class.forName("com.instagram.process.instagram.InstagramApplicationForMainProcess")
            //   and the renamed literal "com.opengram.process.instagram..." failed
            //   to resolve (the real class is still at the original path) →
            //   ClassNotFoundException → process killed.
            // dexlib2 reports each class's type as a smali descriptor
            // `Lcom/instagram/X/Y;`; we convert to dotted form
            // `com.instagram.X.Y` (including `$` for inner classes) for the
            // exact-equality skip check below.
            val instagramClassNames = mutableSetOf<String>()
            classDefForEach { classDef ->
                val type = classDef.type
                if (type.startsWith("Lcom/instagram/") && type.endsWith(";")) {
                    instagramClassNames +=
                        type.substring(1, type.length - 1).replace('/', '.')
                }
            }

            classDefForEach { classDef ->
                // Cheap read-only pre-filter: skip classes with no matching const-string.
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
                        // Skip if this is an exact Java class name in dex — it's a
                        // reflection target (Class.forName / ComponentName / etc.) and
                        // the actual class stays at the original com.instagram.* path.
                        if (original in instagramClassNames) return@forEachIndexed

                        val rewritten = NAMESPACE_WORD_REGEX.replace(original, newReplacement)
                        val register = (instruction as OneRegisterInstruction).registerA
                        // Smali source is line-oriented and uses C-style escapes inside
                        // double-quoted string literals. Beyond `\` and `"`, we MUST also
                        // escape newlines / carriage returns / tabs / form-feed / backspace,
                        // plus any other control char (<0x20 or 0x7F) — otherwise the
                        // generated `const-string vN, "..."` line gets split by the parser
                        // and produces "parser + lexer syntax errors". 45 dex strings in
                        // IG v426.0.0.37.68 contain literal newlines (Room DB schema
                        // mismatch templates: `<table>(com.instagram.X.Entity).\n Expected:\n…`).
                        method.replaceInstruction(
                            index,
                            "const-string v$register, \"${rewritten.toSmaliEscaped()}\"",
                        )
                    }
                }
            }
        }
    }

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.isConstString(): Boolean =
    opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.constStringValue(): String? =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

// ─────────────────────────────────────────────────────────────────────────────
// Manifest helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun rewritePackageReferences(element: Element, newNamespacePrefix: String) {
    val replacement = Regex.escapeReplacement(newNamespacePrefix)
    val tagName = element.tagName

    val attributes = element.attributes
    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        // Skip class-reference attributes — renaming would point at a Java
        // class path that doesn't exist in dex. See SKIP_CLASS_REFERENCE_ATTRS.
        if ((tagName to attribute.nodeName) in SKIP_CLASS_REFERENCE_ATTRS) continue
        val original = attribute.nodeValue ?: continue
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

private fun overrideLauncherLabels(manifest: Element, newLabel: String) {
    val candidates = mutableListOf<Element>()
    walk(manifest) { node ->
        if (node.nodeType != Node.ELEMENT_NODE) return@walk
        val element = node as Element
        if (element.tagName != "activity" && element.tagName != "activity-alias") return@walk
        if (hasLauncherIntentFilter(element)) candidates += element
    }
    candidates.forEach { it.setAttribute("android:label", newLabel) }
}

private fun hasLauncherIntentFilter(element: Element): Boolean {
    val intentFilters = element.getElementsByTagName("intent-filter")
    for (i in 0 until intentFilters.length) {
        val intentFilter = intentFilters.item(i) as Element

        val actions = intentFilter.getElementsByTagName("action")
        var hasMainAction = false
        for (j in 0 until actions.length) {
            if ((actions.item(j) as Element).getAttribute("android:name") == MAIN_ACTION) {
                hasMainAction = true
                break
            }
        }
        if (!hasMainAction) continue

        val categories = intentFilter.getElementsByTagName("category")
        for (j in 0 until categories.length) {
            if ((categories.item(j) as Element).getAttribute("android:name") == LAUNCHER_CATEGORY) {
                return true
            }
        }
    }
    return false
}

private fun walk(node: Node, visit: (Node) -> Unit) {
    visit(node)
    val children = node.childNodes
    for (i in 0 until children.length) walk(children.item(i), visit)
}

/**
 * Escapes a Java/Kotlin string for safe embedding inside a smali
 * `const-string` literal. Smali uses C-style escapes inside `"..."`.
 *
 * Without this, dex strings that contain raw newlines (Room DB schema
 * mismatch templates, multi-line JSON, etc.) break the smali parser when
 * we re-emit them through `replaceInstruction(..., "const-string ...")`.
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
