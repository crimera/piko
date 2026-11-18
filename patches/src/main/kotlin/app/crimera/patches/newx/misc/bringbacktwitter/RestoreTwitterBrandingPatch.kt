/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.bringbacktwitter

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.patch.resourceXmlOrNull
import app.morphe.patcher.xml.ResourceXml
import app.morphe.patcher.xml.XmlElement
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SDK_INT_FIELD = "Landroid/os/Build\$VERSION;->SDK_INT:I"
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

internal fun hasObjectAndIntParameterShape(
    parameterTypes: List<CharSequence>,
    objectType: String? = null,
): Boolean {
    val descriptors = parameterTypes.map(CharSequence::toString)
    // One Int, the callback object, and any auxiliary object parameters (12.41 appended a
    // completion lambda); auxiliary primitives are not part of the contract.
    return descriptors.size >= 2 &&
        descriptors.count { it == "I" } == 1 &&
        descriptors.all { it == "I" || it.startsWith("L") } &&
        if (objectType == null) {
            descriptors.any { it.startsWith("L") }
        } else {
            descriptors.count { it == objectType } == 1
        }
}

/** The normal-dismiss fallback: the callback object plus auxiliary objects, no Int. */
internal fun isLaunchRevealFallbackShape(parameterTypes: List<CharSequence>, callbackType: String): Boolean {
    val descriptors = parameterTypes.map(CharSequence::toString)
    return descriptors.firstOrNull() == callbackType &&
        descriptors.none { it == "I" } &&
        descriptors.all { it.startsWith("L") }
}

internal fun isLaunchRevealRenderer(method: Method): Boolean =
    AccessFlags.STATIC.isSet(method.accessFlags) &&
        hasObjectAndIntParameterShape(method.parameterTypes)

private object TwitterLaunchRevealRendererFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = SURFACE_CONTROL_BUILDER_DESCRIPTOR,
            name = "build",
            parameters = emptyList(),
            returnType = "Landroid/view/SurfaceControl;",
        ),
        methodCall(
            definingClass = ATTACHED_SURFACE_CONTROL_DESCRIPTOR,
            name = "buildReparentTransaction",
            parameters = listOf("Landroid/view/SurfaceControl;"),
            returnType = "Landroid/view/SurfaceControl\$Transaction;",
        ),
    ),
    custom = { method, _ -> isLaunchRevealRenderer(method) },
)

private object TwitterLaunchRevealMarkerFingerprint : Fingerprint(
    filters = listOf(string("LaunchReveal")),
)

private fun Method.launchRevealViewParameter(): String? {
    val parameters = parameterTypes.map(CharSequence::toString)
    return when {
        !AccessFlags.STATIC.isSet(accessFlags) && parameters.size == 1 ->
            parameters[0].takeIf { it.startsWith("L") }
        AccessFlags.STATIC.isSet(accessFlags) &&
            parameters.size == 2 &&
            parameters[0] == definingClass ->
            parameters[1].takeIf { it.startsWith("L") }
        else -> null
    }
}

private fun isLaunchRevealCallback(method: Method): Boolean {
    if (method.returnType != "V") return false
    val callbackParameter = method.launchRevealViewParameter() ?: return false

    val staticCalls = method.staticInvokeReferences().map { it.second }
    val revealTargets =
        staticCalls.filter { reference ->
            hasObjectAndIntParameterShape(reference.parameterTypes, callbackParameter) &&
                reference.returnType == "V"
        }
    val fallbackTargets =
        staticCalls.filter { reference ->
            isLaunchRevealFallbackShape(reference.parameterTypes, callbackParameter) &&
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
    return references.any {
        it.definingClass in setOf("Landroid/view/View;", "Landroid/window/SplashScreenView;") &&
            it.name == "animate"
    } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "alpha" } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "setDuration" } &&
        references.any { it.definingClass == "Landroid/view/ViewPropertyAnimator;" && it.name == "withEndAction" }
}

private fun validateNormalSplashDismiss(reference: MethodReference) {
    val owner =
        classDefByOrNull(reference.definingClass)
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
    patch {
        unsupportedOnMount(default = false)
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")

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
            val callbackParameter =
                callbackMethod.launchRevealViewParameter()
                    ?: throw PatchException(
                        "NewX launch reveal callback has unexpected parameters: ${callback.originalMethod}",
                    )
            val fallbackCalls =
                callbackMethod.staticInvokeReferences().filter { (_, reference) ->
                    reference.definingClass == renderer.definingClass &&
                        isLaunchRevealFallbackShape(reference.parameterTypes, callbackParameter) &&
                        reference.returnType == "V"
                }
            val fallbackCall =
                requireExactlyOne(
                    label = "NewX normal splash dismiss call in ${callback.originalMethod}",
                    candidates = fallbackCalls,
                    describe = { "instruction ${it.first}: ${it.second.descriptor()}" },
                )
            validateNormalSplashDismiss(fallbackCall.second)

            // The app already routes to the normal dismiss when the reveal is unavailable (API < 33,
            // reduced motion, animations off). Force that arm by zeroing the SDK value at its gate
            // instead of re-creating the fallback's arguments, which gain lambdas across releases.
            val instructions = callbackMethod.instructions.toList()
            val sdkGates =
                instructions.mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode != Opcode.SGET) return@mapIndexedNotNull null
                    if (instruction.getReference<FieldReference>()?.toString() != SDK_INT_FIELD) {
                        return@mapIndexedNotNull null
                    }
                    val sdkRegister = (instruction as OneRegisterInstruction).registerA
                    val gate =
                        (index + 1 until minOf(index + 6, instructions.size)).firstOrNull { candidate ->
                            val next = instructions[candidate]
                            next.opcode == Opcode.IF_LT && (next as TwoRegisterInstruction).registerA == sdkRegister
                        } ?: return@mapIndexedNotNull null
                    val branch = instructions[gate] as? BuilderOffsetInstruction ?: return@mapIndexedNotNull null
                    val target = branch.target.location.index
                    val fallbackIndex =
                        (target until instructions.size).firstOrNull { candidate ->
                            instructions[candidate].getReference<MethodReference>()?.toString() ==
                                fallbackCall.second.toString()
                        } ?: return@mapIndexedNotNull null
                    // The forced arm must not read the zeroed register before redefining it.
                    val written = mutableSetOf<Int>()
                    for (cursor in target until fallbackIndex) {
                        val arm = instructions[cursor]
                        val destination = if (arm.opcode.setsRegister()) (arm as? OneRegisterInstruction)?.registerA
                            ?: (arm as? TwoRegisterInstruction)?.registerA
                            ?: (arm as? ThreeRegisterInstruction)?.registerA else null
                        val reads = arm.registersUsed.filter { it != destination || arm.opcode in INVOKE_OPCODES }
                        if (sdkRegister in reads && sdkRegister !in written) return@mapIndexedNotNull null
                        if (destination != null) written += destination
                    }
                    if (sdkRegister in instructions[fallbackIndex].registersUsed && sdkRegister !in written) {
                        return@mapIndexedNotNull null
                    }
                    gate to sdkRegister
                }
            val booleanGates =
                if (sdkGates.isNotEmpty()) {
                    emptyList()
                } else {
                    instructions.mapIndexedNotNull { index, instruction ->
                        if (instruction.opcode != Opcode.IGET_BOOLEAN) return@mapIndexedNotNull null
                        val gateRegister = (instruction as TwoRegisterInstruction).registerA
                        val gate =
                            (index + 1 until minOf(index + 4, instructions.size)).firstOrNull { candidate ->
                                val next = instructions[candidate]
                                next.opcode == Opcode.IF_EQZ &&
                                    (next as OneRegisterInstruction).registerA == gateRegister
                            } ?: return@mapIndexedNotNull null
                        val branch = instructions[gate] as? BuilderOffsetInstruction ?: return@mapIndexedNotNull null
                        val target = branch.target.location.index
                        val fallbackIndex =
                            (target until instructions.size).firstOrNull { candidate ->
                                instructions[candidate].getReference<MethodReference>()?.toString() ==
                                    fallbackCall.second.toString()
                            } ?: return@mapIndexedNotNull null
                        val written = mutableSetOf<Int>()
                        for (cursor in target until fallbackIndex) {
                            val arm = instructions[cursor]
                            val destination = if (arm.opcode.setsRegister()) (arm as? OneRegisterInstruction)?.registerA
                                ?: (arm as? TwoRegisterInstruction)?.registerA
                                ?: (arm as? ThreeRegisterInstruction)?.registerA else null
                            val reads = arm.registersUsed.filter { it != destination || arm.opcode in INVOKE_OPCODES }
                            if (gateRegister in reads && gateRegister !in written) return@mapIndexedNotNull null
                            if (destination != null) written += destination
                        }
                        if (gateRegister in instructions[fallbackIndex].registersUsed && gateRegister !in written) {
                            return@mapIndexedNotNull null
                        }
                        gate to gateRegister
                    }
                }
            val (gateIndex, gateRegister) =
                requireExactlyOne(
                    label = "NewX launch reveal gate in ${callback.originalMethod}",
                    candidates = sdkGates.ifEmpty { booleanGates },
                    describe = { "gate at ${it.first} on v${it.second}" },
                )

            // Relocate old-entry branches so none can bypass the forced value.
            callbackMethod.insertHook(index = gateIndex, relocateBranchTargets = true) {
                constInt(gateRegister, 0)
            }
        }
    }

@Suppress("unused")
val bringBackTwitterPatch =
    patch(
        name = "NewX: Restore Twitter branding",
        description = "Bring back old Twitter icons and name",
        default = false,
    ) {
        unsupportedOnMount(default = false)
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")

        dependsOn(addResourcesPatch, restoreTwitterLaunchRevealPatch)

        execute {
            val hasPullToRefreshLogo = resourceFile("res/raw/x_refresh_logo.json").exists()
            addAppResources("twitter-bring-back")
            ensureNightSplashBackground()
            
            resourceXml("AndroidManifest.xml").use { manifest ->
                manifest.elements("application").first().setAttribute("android:label", "Twitter")
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
            val whiteBird = resourceFile("res/drawable/ic_vector_twitter_white.xml")
            whiteBird.copyTo(resourceFile("res/drawable/ic_stat_x.xml", copy = false), overwrite = true)
            whiteBird.copyTo(resourceFile("res/drawable/ic_logo_x.xml", copy = false), overwrite = true)
            resourceXml("res/drawable/ic_logo_x.xml").use { doc ->
                doc.elements("vector").firstOrNull()?.let { vector ->
                    vector.setAttribute("android:height", "96.0dp")
                    vector.setAttribute("android:width", "96.0dp")
                }
            }

            // Replace app icon background colors.
            // AddResourcesPatch only overrides bring-back colors that already exist in the target
            // app, so define them here for versions that renamed or dropped them. Otherwise the
            // references below would dangle and the resource table would fail to encode.
            ensureDefaultBrandingColors()
            resourceXml("res/values/colors.xml").use { colors ->
                colors.resourceOrNull("ic_launcher_background")?.text = "@color/twitter_blue"
            }

            // endregion

            listOf("mipmap-anydpi", "mipmap-anydpi-v26").forEach { dir ->
                listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { file ->
                    resourceXmlOrNull("res/$dir/$file")?.use { doc ->
                        doc.elements("foreground").firstOrNull()
                            ?.setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                        doc.elements("monochrome").firstOrNull()
                            ?.setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                    }
                }
            }

            sizes.forEach { size ->
                val twitterIcon = resourceFile("res/mipmap-$size/ic_launcher_twitter.webp")
                val twitterRoundIcon = resourceFile("res/mipmap-$size/ic_launcher_twitter_round.webp")
                if (twitterIcon.exists()) {
                    twitterIcon.copyTo(resourceFile("res/mipmap-$size/ic_launcher.webp", copy = false), overwrite = true)
                }
                if (twitterRoundIcon.exists()) {
                    twitterRoundIcon.copyTo(resourceFile("res/mipmap-$size/ic_launcher_round.webp", copy = false), overwrite = true)
                }
            }

            resourceXmlOrNull("res/drawable/\$splash_screen_icon_animated__0.xml")?.use { animatedSplash ->
                val birdPath =
                    resourceXml("res/drawable/splash_screen_icon.xml").use { doc ->
                        doc.elements("path").firstOrNull()?.let { it.attribute("android:pathData").orEmpty() }
                    }
                if (birdPath != null) {
                    animatedSplash.elements("path").firstOrNull()?.setAttribute("android:pathData", birdPath)
                }
            }

            // Replace splash icon & colors
            resourceXmlOrNull("res/values/styles.xml")?.use { styles ->
                val styleElement =
                    styles.resourceOrNull("Theme.LaunchScreen")
                        ?: styles.resourceOrNull("Theme.X.SplashScreen")

                styleElement?.let { style ->
                    style.styleItem("windowSplashScreenAnimatedIcon")?.text = "@drawable/splash_screen_icon_animated"
                    style.styleItem("windowSplashScreenBackground")?.text = SPLASH_BACKGROUND
                }
            }

            // Android 13+ may suppress the splash icon unless icon display is preferred.
            resourceXmlOrNull("res/values-v31/styles.xml")?.use { styles ->
                styles.resourceOrNull("Theme.SplashScreen")?.let { style ->
                    val behavior =
                        style.styleItem("android:windowSplashScreenBehavior")
                            ?: style.appendChild("item") { setAttribute("name", "android:windowSplashScreenBehavior") }
                    behavior.text = "icon_preferred"
                }
            }
        }
    }

private fun XmlElement.styleItem(name: String): XmlElement? =
    children.firstOrNull { it.attribute("name") == name }

/**
 * Opens a values file of the APK, or an empty one to add resources to if the APK has none.
 */
private fun valuesXml(path: String): ResourceXml =
    resourceXmlOrNull(path) ?: resourceXml(path, copy = false)

private fun ensureDefaultBrandingColors() {
    valuesXml("res/values/colors.xml").use { colors ->
        for ((name, value) in listOf(
            "twitter_blue" to "#1d9bf0",
            "twitter_splash_background" to "@color/twitter_blue",
        )) {
            val existing = colors.resourceOrNull(name)
            if (existing != null) {
                existing.text = value
                continue
            }

            colors.root.appendChild("color") {
                setAttribute("name", name)
                text = value
            }
        }
    }
}

private fun ensureNightSplashBackground() {
    valuesXml("res/values-night/colors.xml").use { colors ->
        val existing = colors.resourceOrNull("twitter_splash_background")
        if (existing != null) {
            existing.text = "#000000"
            return@use
        }

        colors.root.appendChild("color") {
            setAttribute("name", "twitter_splash_background")
            text = "#000000"
        }
    }
}
