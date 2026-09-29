/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.changeLikeAnimation

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.settings.IgFragmentActivityOnCreate
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/feed/ChangeLikeAnimationPatch;"

internal object ChangeLikeAnimationExtensionFingerprint : Fingerprint(
    name = "animationClass",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object XDTUserActivationMetadataImplInitFingerprint : Fingerprint(
    name = "<init>",
    definingClass = "Lcom/instagram/api/schemas/XDTUserActivationMetadataImpl;",
)

private const val LIKE_VIEW = "Lcom/instagram/ui/mediaactions/LikeActionView;"
private const val CONTEXT = "Landroid/content/Context;"

context(context: BytecodePatchContext)
private fun installAnimationRendering(animationType: String) {
    val setup = Fingerprint(name = "setUpCustomLikesAnimation", definingClass = LIKE_VIEW)
        .matchAll(0..Int.MAX_VALUE).singleOrNull()?.method
        ?: throw PatchException("Expected one custom like animation setup")
    val renderType = setup.parameterTypes.singleOrNull()?.toString()
        ?: throw PatchException("Unexpected custom like animation setup signature")
    val renderClass = context.classDefBy(renderType)
    if (renderClass.superclass != "Ljava/lang/Enum;") {
        throw PatchException("Expected a like animation rendering enum")
    }
    val mapper = renderClass.methods.singleOrNull {
        it.parameterTypes == listOf(animationType) && it.returnType == renderType &&
            AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags)
    } ?: throw PatchException("Expected one like animation enum mapper")
    val configure = Fingerprint(
        definingClass = LIKE_VIEW,
        parameters = listOf(USER_SESSION_CLASS, renderType),
        returnType = "V",
    ).matchAll(0..Int.MAX_VALUE).singleOrNull()?.method
        ?: throw PatchException("Expected one like animation view configuration method")
    if (configure.instructions.none { it.getReference<MethodReference>()?.toString() == setup.toString() }) {
        throw PatchException("Like animation view configuration does not call custom animation setup")
    }
    val defaultFactory = configure.instructions.filter { it.opcode == Opcode.INVOKE_VIRTUAL }
        .mapNotNull { it.getReference<MethodReference>() }.singleOrNull {
            it.parameterTypes == listOf(CONTEXT) &&
                context.classDefByOrNull(it.returnType)?.superclass == "Landroid/graphics/drawable/Drawable;"
        } ?: throw PatchException("Expected one default heart drawable factory")
    val factoryInstance = configure.instructions.filter { it.opcode == Opcode.SGET_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }.singleOrNull {
            it.type == defaultFactory.definingClass
        } ?: throw PatchException("Expected one default heart factory instance")
    val defaultStart = Fingerprint(name = "onAnimationStart", definingClass = LIKE_VIEW)
        .matchAll(0..Int.MAX_VALUE).singleOrNull()?.method
        ?: throw PatchException("Expected one default heart animation start callback")
    val colorize = defaultStart.instructions.filter { it.opcode == Opcode.INVOKE_VIRTUAL }
        .mapNotNull { it.getReference<MethodReference>() }.singleOrNull {
            it.definingClass == defaultFactory.returnType &&
                it.parameterTypes == listOf(CONTEXT) && it.returnType == "V"
        } ?: throw PatchException("Expected one default heart color initialization call")
    for (method in listOf(defaultFactory, colorize)) {
        val targetClass = context.classDefBy(method.definingClass)
        if (!AccessFlags.PUBLIC.isSet(targetClass.accessFlags) || targetClass.methods.none {
                it.toString() == method.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                    !AccessFlags.STATIC.isSet(it.accessFlags)
            }) {
            throw PatchException("Default heart method is not public: $method")
        }
    }
    if (context.classDefBy(factoryInstance.definingClass).fields.none {
            it.toString() == factoryInstance.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                AccessFlags.STATIC.isSet(it.accessFlags)
        }) {
        throw PatchException("Default heart factory instance is not public static")
    }
    val defaultBridge = Fingerprint(name = "defaultPreviewDrawable", definingClass = EXTENSION_CLASS_DESCRIPTOR).method
    val defaultPreview = MutableMethod(ImmutableMethod(
        defaultBridge.definingClass, defaultBridge.name, defaultBridge.parameters, defaultBridge.returnType,
        defaultBridge.accessFlags, defaultBridge.annotations, defaultBridge.hiddenApiRestrictions,
        ImmutableMethodImplementation(2, emptyList(), emptyList(), emptyList()),
    )).apply {
        addInstructions(
            0,
            """
            sget-object v0, $factoryInstance
            invoke-virtual {v0, p0}, $defaultFactory
            move-result-object v0
            if-eqz v0, :return_heart
            invoke-virtual {v0, p0}, $colorize
            :return_heart
            return-object v0
            """.trimIndent(),
        )
    }
    context.mutableClassDefBy(EXTENSION_CLASS_DESCRIPTOR).methods.apply {
        remove(defaultBridge)
        add(defaultPreview)
    }
    configure.addInstructions(
        0,
        """
        invoke-static/range {p2 .. p2}, $EXTENSION_CLASS_DESCRIPTOR->changeRenderAnimation(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object p2
        check-cast p2, $renderType
        """.trimIndent(),
    )
    Fingerprint(name = "mapAnimation", definingClass = EXTENSION_CLASS_DESCRIPTOR).method.addInstructions(
        0,
        """
        check-cast p0, $animationType
        invoke-static/range {p0 .. p0}, $mapper
        move-result-object p0
        return-object p0
        """.trimIndent(),
    )
    val factory = setup.instructions.filter { it.opcode == Opcode.INVOKE_STATIC }
        .mapNotNull { it.getReference<MethodReference>() }.singleOrNull {
            it.parameterTypes == listOf(CONTEXT, renderType) && it.returnType.startsWith("L")
        } ?: throw PatchException("Expected one like animation drawable factory")
    val factoryMethod = context.classDefBy(factory.definingClass).methods.singleOrNull {
        it.toString() == factory.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
            AccessFlags.STATIC.isSet(it.accessFlags)
    } ?: throw PatchException("Like animation drawable factory is not public static")

    val drawableTypes = mutableSetOf<String>()
    var type = factoryMethod.returnType
    while (type != "Landroid/graphics/drawable/Drawable;") {
        if (!drawableTypes.add(type)) throw PatchException("Invalid animation drawable hierarchy")
        type = context.classDefByOrNull(type)?.superclass
            ?: throw PatchException("Like animation factory does not return a Drawable")
    }
    val playback = Fingerprint(definingClass = LIKE_VIEW, parameters = listOf("Z", "F"), returnType = "V")
        .matchAll(0..Int.MAX_VALUE).singleOrNull()?.method
        ?: throw PatchException("Expected one like animation playback method")
    val start = playback.instructions.filter { it.opcode == Opcode.INVOKE_VIRTUAL }
        .mapNotNull { it.getReference<MethodReference>() }.singleOrNull {
            it.definingClass in drawableTypes && it.parameterTypes.isEmpty() && it.returnType == "V"
        } ?: throw PatchException("Expected one native animation start call")
    val controls = context.classDefBy(start.definingClass).methods
    if (controls.none { it.toString() == start.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags) }) {
        throw PatchException("Native animation start method is not public")
    }
    val stop = controls.singleOrNull {
        it.name == "stop" && it.parameterTypes.isEmpty() && it.returnType == "V" &&
            AccessFlags.PUBLIC.isSet(it.accessFlags)
    } ?: throw PatchException("Expected one native animation stop method")

    Fingerprint(name = "previewDrawable", definingClass = EXTENSION_CLASS_DESCRIPTOR).method.addInstructions(
        0,
        """
        invoke-static {p1}, $animationType->valueOf(Ljava/lang/String;)$animationType
        move-result-object p1
        invoke-static {p1}, $mapper
        move-result-object p1
        invoke-static {p0, p1}, $factory
        move-result-object p0
        return-object p0
        """.trimIndent(),
    )
    for ((name, method) in listOf("startPreview" to start, "stopPreview" to stop)) {
        Fingerprint(name = name, definingClass = EXTENSION_CLASS_DESCRIPTOR).method.addInstructions(
            0,
            """
            check-cast p0, ${start.definingClass}
            invoke-virtual {p0}, $method
            return-void
            """.trimIndent(),
        )
    }
}

@Suppress("unused")
val changeLikeAnimationPatch =
    bytecodePatch(
        name = "Change like animation",
        description = "Change the animation to one from existing Rings like animations",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            IgFragmentActivityOnCreate.method.apply {
                addInstruction(
                    indexOfFirstInstruction(Opcode.RETURN_VOID),
                    "invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->onActivityCreated()V",
                )
            }

            XDTUserActivationMetadataImplInitFingerprint.method.apply {
                val animationEnumClassType = parameters[0].type
                ChangeLikeAnimationExtensionFingerprint.changeString("className", classNameToExtension(animationEnumClassType))
                installAnimationRendering(animationEnumClassType)

                addInstructions(
                    0,
                    """
                    sget-object p2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->changeLikeAnimation(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :set_animation
                    sget-object p2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                    :set_animation
                    check-cast v0, $animationEnumClassType
                    move-object/from16 p1, v0
                    """.trimIndent(),
                )
                enableSettings("changeLikeAnimation")
            }
        }
    }
