/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.bringbacktwitter

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.string
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.findElementByAttributeValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import java.io.FileWriter
import java.nio.file.Files

private const val SPLASH_BACKGROUND = "@color/twitter_splash_background"

private const val SURFACE_CONTROL_BUILDER_DESCRIPTOR = "Landroid/view/SurfaceControl\$Builder;"
private const val ATTACHED_SURFACE_CONTROL_DESCRIPTOR = "Landroid/view/AttachedSurfaceControl;"

internal fun resolveLaunchRevealRenderer(
    markerMethods: Collection<Method>,
    rendererCandidates: Collection<Method>,
): Method? {
    if (markerMethods.isEmpty()) return null
    return requireExactlyOne(
        label = "NewX launch reveal renderer after marker",
        candidates = rendererCandidates,
    )
}

private fun Method.codeInstructions() = implementation?.instructions?.toList().orEmpty()

private fun Method.invokeReferences(): List<Pair<Int, MethodReference>> =
    codeInstructions().mapIndexedNotNull { index, instruction ->
        if (instruction.opcode !in INVOKE_OPCODES) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        index to reference
    }

private val INVOKE_OPCODES =
    setOf(
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_SUPER,
        Opcode.INVOKE_SUPER_RANGE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_DIRECT_RANGE,
        Opcode.INVOKE_STATIC,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_INTERFACE_RANGE,
    )

private fun Method.staticInvokeReferences(): List<Pair<Int, MethodReference>> =
    codeInstructions().mapIndexedNotNull { index, instruction ->
        if (instruction.opcode !in STATIC_INVOKE_OPCODES) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        index to reference
    }

private val STATIC_INVOKE_OPCODES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)

private fun Method.hasInvoke(reference: (MethodReference) -> Boolean): Boolean =
    invokeReferences().any { (_, candidate) -> reference(candidate) }

internal fun isLaunchRevealRenderer(method: Method): Boolean =
    AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.returnType == "V" &&
        method.parameterTypes.size == 2 &&
        method.parameterTypes[0].toString().startsWith("L") &&
        method.parameterTypes[1].toString() == "I" &&
        method.hasInvoke { reference ->
            reference.definingClass == SURFACE_CONTROL_BUILDER_DESCRIPTOR &&
                reference.name == "build" &&
                reference.parameterTypes.isEmpty() &&
                reference.returnType == "Landroid/view/SurfaceControl;"
        } &&
        method.hasInvoke { reference ->
            reference.definingClass == ATTACHED_SURFACE_CONTROL_DESCRIPTOR &&
                reference.name == "buildReparentTransaction" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/SurfaceControl;") &&
                reference.returnType == "Landroid/view/SurfaceControl\$Transaction;"
        }

private object TwitterLaunchRevealRendererFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ -> isLaunchRevealRenderer(method) },
)

private object TwitterLaunchRevealMarkerFingerprint : Fingerprint(
    filters = listOf(string("LaunchReveal")),
)

private fun isLaunchRevealCallback(method: Method): Boolean {
    if (AccessFlags.STATIC.isSet(method.accessFlags) || method.returnType != "V" || method.parameterTypes.size != 1) return false
    val callbackParameter = method.parameterTypes.single().toString()
    if (!callbackParameter.startsWith("L")) return false

    val staticCalls = method.staticInvokeReferences().map { it.second }
    val revealTargets =
        staticCalls.filter { reference ->
            reference.parameterTypes.map(CharSequence::toString) == listOf(callbackParameter, "I") &&
                reference.returnType == "V"
        }
    val fallbackTargets =
        staticCalls.filter { reference ->
            reference.parameterTypes.map(CharSequence::toString) == listOf(callbackParameter) &&
                reference.returnType == "V"
        }
    if (revealTargets.size != 1 || fallbackTargets.size != 1) return false
    if (revealTargets.single().definingClass != fallbackTargets.single().definingClass) return false

    return true
}

private object TwitterLaunchRevealCallbackFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ -> isLaunchRevealCallback(method) },
)

private fun MethodReference.descriptor(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun Method.descriptor(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun isNormalSplashDismiss(method: Method): Boolean {
    val references = method.invokeReferences().map { it.second }
    return references.any { it.definingClass == "Landroid/view/View;" && it.name == "animate" } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "alpha" } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "setDuration" } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "withEndAction" }
}

context(context: BytecodePatchContext)
private fun validateNormalSplashDismiss(reference: MethodReference) {
    val owner =
        context.classDefByOrNull(reference.definingClass)
            ?: throw PatchException("NewX splash dismiss owner was not found: ${reference.definingClass}")
    val method =
        requireExactlyOne(
            label = "NewX normal splash dismiss method",
            candidates = owner.methods.filter { candidate ->
                candidate.name == reference.name &&
                    candidate.parameterTypes.map(CharSequence::toString) ==
                        reference.parameterTypes.map(CharSequence::toString) &&
                    candidate.returnType == reference.returnType
            },
            describe = { it.toString() },
        )
    if (!isNormalSplashDismiss(method)) {
        throw PatchException("NewX splash fallback no longer performs the normal dismiss animation: $method")
    }
}

private val restoreTwitterLaunchRevealPatch =
    bytecodePatch(
        name = "NewX: Restore Twitter splash transition",
        description = "Skip the X launch reveal when Twitter branding is restored.",
        default = false,
    ) {
        unsupportedOnMount(default = false)
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val markerMethods = TwitterLaunchRevealMarkerFingerprint.scopedMatchAllOrNull().orEmpty()
            val rendererMatches = TwitterLaunchRevealRendererFingerprint.scopedMatchAllOrNull().orEmpty()
            val renderer =
                resolveLaunchRevealRenderer(
                    markerMethods.map { it.originalMethod },
                    rendererMatches.map { it.originalMethod },
                ) ?: return@execute
            val rendererDescriptor = renderer.descriptor()

            val callbacks =
                TwitterLaunchRevealCallbackFingerprint.scopedMatchAllOrNull().orEmpty().filter { match ->
                    match.method.invokeReferences().any { (_, reference) ->
                        reference.descriptor() == rendererDescriptor
                    }
                }
            val callback =
                requireExactlyOne(
                    label = "NewX launch reveal callback",
                    candidates = callbacks,
                    describe = { it.originalMethod.toString() },
                )

            val callbackMethod = callback.method
            val fallbackCalls =
                callbackMethod.staticInvokeReferences().filter { (_, reference) ->
                    reference.definingClass == renderer.definingClass &&
                        reference.parameterTypes.map(CharSequence::toString) ==
                            listOf(renderer.parameterTypes.first().toString()) &&
                        reference.returnType == "V"
                }
            val fallbackCall =
                requireExactlyOne(
                    label = "NewX normal splash dismiss call in ${callback.originalMethod}",
                    candidates = fallbackCalls,
                    describe = { "instruction ${it.first}: ${it.second.descriptor()}" },
                )
            validateNormalSplashDismiss(fallbackCall.second)
            val callbackInputRegister = callbackMethod.p0Register + 1

            callbackMethod.insertHook(
                index = 0,
                // The argument is untouched at entry; route old-entry branches through the fade and keep structured labels unchanged.
                relocateBranchTargets = true,
            ) {
                invokeStatic(fallbackCall.second, callbackInputRegister)
                returnVoid()
            }
        }
    }

@Suppress("unused")
val bringBackTwitterPatch =
    resourcePatch(
        name = "NewX: Restore Twitter branding",
        description = "Bring back old Twitter icons and name",
        default = false,
    ) {
        unsupportedOnMount(default = false)
        compatibleWith(COMPATIBILITY_NEW_X)

        dependsOn(addResourcesPatch, restoreTwitterLaunchRevealPatch)

        execute {
            val hasPullToRefreshLogo = get("res/raw/x_refresh_logo.json").exists()
            addAppResources("twitter-bring-back")
            ensureNightSplashBackground()
            
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element
                application.setAttribute("android:label", "Twitter")
            }

            // region Change app icons

            val mipmapIcons =
                arrayOf(
                    "ic_launcher_twitter.webp",
                    "ic_launcher_twitter_round.webp",
                    "ic_launcher_twitter_foreground.webp",
                )

            val drawableIcons =
                arrayOf(
                    "ic_vector_twitter.xml",
                    "ic_vector_x.xml",
                    "ic_vector_home.xml",
                    "ic_vector_twitter_white.xml",
                    "ic_vector_home_stroke.xml",
                    "splash_screen_icon.xml",
                )

            val sizes =
                arrayOf(
                    "xxxhdpi",
                    "xxhdpi",
                    "xhdpi",
                    "hdpi",
                    "mdpi",
                )

            // drawable icons
            sizes
                .map { "drawable-$it" }
                .plus("drawable")
                .map {
                    if (it == "drawable") {
                        ResourceGroup(it, *drawableIcons)
                    } else {
                        ResourceGroup(it, "ic_stat_twitter.webp")
                    }
                }.forEach {
                    copyResources("twitter/bringbacktwitter", it)
                }

            // NewX 12.34 renders the pull-to-refresh logo from this Lottie resource, bypassing the drawable overrides.
            if (hasPullToRefreshLogo) {
                copyResources(
                    "twitter/bringbacktwitter",
                    ResourceGroup("raw", "x_refresh_logo.json"),
                )
            }

            // mipmap icons
            sizes
                .map { "mipmap-$it" }
                .map {
                    if (it == "mipmap-xxhdpi") {
                        ResourceGroup(it, *mipmapIcons.plus("fg_launcher_twitter.webp"))
                    } else {
                        ResourceGroup(it, *mipmapIcons)
                    }
                }.forEach {
                    copyResources("twitter/bringbacktwitter", it)
                }

            // Notification icons reuse the white bird vector under different names.
            // (copyResources is name-matched, so this copy happens in res directly.)
            val whiteBird = get("res/drawable/ic_vector_twitter_white.xml")
            whiteBird.copyTo(get("res/drawable/ic_stat_x.xml", copy = false), overwrite = true)
            whiteBird.copyTo(get("res/drawable/ic_logo_x.xml", copy = false), overwrite = true)
            document("res/drawable/ic_logo_x.xml").use { doc ->
                doc.getElementsByTagName("vector").item(0)?.let { vector ->
                    (vector as Element).setAttribute("android:height", "96.0dp")
                    vector.setAttribute("android:width", "96.0dp")
                }
            }

            // Replace app icon background colors.
            // AddResourcesPatch only overrides bring-back colors that already exist in the target
            // app, so define them here for versions that renamed or dropped them. Otherwise the
            // references below would dangle and the resource table would fail to encode.
            ensureDefaultBrandingColors()
            document("res/values/colors.xml").use { document ->
                document.childNodes.findElementByAttributeValue("name", "ic_launcher_background")?.textContent = "@color/twitter_blue"
            }

            // endregion

            listOf("mipmap-anydpi", "mipmap-anydpi-v26").forEach { dir ->
                listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { file ->
                    // Resource files are decoded on exact-path reads, not by resolving from `res`.
                    val launcherXml = get("res/$dir/$file")
                    if (launcherXml.exists()) {
                        document("res/$dir/$file").use { doc ->
                            doc.getElementsByTagName("foreground").item(0)?.let {
                                (it as Element).setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                            }
                            doc.getElementsByTagName("monochrome").item(0)?.let {
                                (it as Element).setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                            }
                        }
                    }
                }
            }

            sizes.forEach { size ->
                val twitterIcon = get("res/mipmap-$size/ic_launcher_twitter.webp")
                val twitterRoundIcon = get("res/mipmap-$size/ic_launcher_twitter_round.webp")
                if (twitterIcon.exists()) {
                    twitterIcon.copyTo(get("res/mipmap-$size/ic_launcher.webp", copy = false), overwrite = true)
                }
                if (twitterRoundIcon.exists()) {
                    twitterRoundIcon.copyTo(get("res/mipmap-$size/ic_launcher_round.webp", copy = false), overwrite = true)
                }
            }

            val animatedSplashVector = get("res/drawable/\$splash_screen_icon_animated__0.xml")
            if (animatedSplashVector.exists()) {
                val birdPath =
                    document("res/drawable/splash_screen_icon.xml").use { doc ->
                        doc.getElementsByTagName("path").item(0)?.let { (it as Element).getAttribute("android:pathData") }
                    }
                if (birdPath != null) {
                    document("res/drawable/\$splash_screen_icon_animated__0.xml").use { doc ->
                        doc.getElementsByTagName("path").item(0)?.let {
                            (it as Element).setAttribute("android:pathData", birdPath)
                        }
                    }
                }
            }

            // Replace splash icon & colors
            val stylesXml = get("res/values/styles.xml")
            if (stylesXml.exists()) {
                document("res/values/styles.xml").use { document ->
                    val styleElement =
                        document.childNodes.findElementByAttributeValue("name", "Theme.LaunchScreen")
                            ?: document.childNodes.findElementByAttributeValue("name", "Theme.X.SplashScreen")

                    styleElement?.let { style ->
                        style.childNodes.findElementByAttributeValue("name", "windowSplashScreenAnimatedIcon")?.textContent = "@drawable/splash_screen_icon_animated"
                        style.childNodes.findElementByAttributeValue("name", "windowSplashScreenBackground")?.textContent = SPLASH_BACKGROUND
                    }
                }
            }

            // Android 13+ may suppress the splash icon unless icon display is preferred.
            val v31StylesXml = get("res/values-v31/styles.xml")
            if (v31StylesXml.exists()) {
                document("res/values-v31/styles.xml").use { document ->
                    val splashStyle = document.childNodes.findElementByAttributeValue("name", "Theme.SplashScreen")
                    splashStyle?.let { style ->
                        val behavior =
                            style.childNodes.findElementByAttributeValue(
                                "name",
                                "android:windowSplashScreenBehavior",
                            ) ?: document.createElement("item").also { item ->
                                item.setAttribute("name", "android:windowSplashScreenBehavior")
                                style.appendChild(item)
                            }
                        behavior.textContent = "icon_preferred"
                    }
                }
            }
        }
    }

private fun app.morphe.patcher.patch.ResourcePatchContext.ensureDefaultBrandingColors() {
    val colorsFile = get("res/values/colors.xml")
    if (!colorsFile.exists()) {
        Files.createDirectories(colorsFile.toPath().parent)
        FileWriter(colorsFile).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
        }
    }

    document("res/values/colors.xml").use { document ->
        val colorElements = document.getElementsByTagName("color")
        for ((name, value) in listOf(
            "twitter_blue" to "#1d9bf0",
            "twitter_splash_background" to "@color/twitter_blue",
        )) {
            val existing =
                requireAtMostOne(
                    label = "res/values/colors.xml color '$name'",
                    candidates =
                        (0 until colorElements.length)
                            .mapNotNull { colorElements.item(it) as? Element }
                            .filter { it.getAttribute("name") == name },
                    describe = { "${it.getAttribute("name")}=${it.textContent}" },
                )
            if (existing != null) {
                existing.textContent = value
                continue
            }

            document.documentElement.appendChild(
                document.createElement("color").apply {
                    setAttribute("name", name)
                    textContent = value
                },
            )
        }
    }
}

private fun app.morphe.patcher.patch.ResourcePatchContext.ensureNightSplashBackground() {
    val colorsPath = "res/values-night/colors.xml"
    val colorsFile = get(colorsPath)
    if (!colorsFile.exists()) {
        Files.createDirectories(colorsFile.toPath().parent)
        FileWriter(colorsFile).use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
        }
    }

    document(colorsPath).use { document ->
        val colors = document.getElementsByTagName("color")
        for (index in 0 until colors.length) {
            val color = colors.item(index) as? Element ?: continue
            if (color.getAttribute("name") != "twitter_splash_background") continue
            color.textContent = "#000000"
            return@use
        }

        document.documentElement.appendChild(
            document.createElement("color").apply {
                setAttribute("name", "twitter_splash_background")
                textContent = "#000000"
            },
        )
    }
}
