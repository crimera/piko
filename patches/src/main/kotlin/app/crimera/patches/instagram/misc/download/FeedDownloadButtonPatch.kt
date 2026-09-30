/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val VIEW_DESCRIPTOR = "Landroid/view/View;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"
private const val USER_SESSION_DESCRIPTOR = "Lcom/instagram/common/session/UserSession;"
private const val EXTENSION_METHOD =
    "$DOWNLOAD_DESCRIPTOR/DownloadUtils;->addFeedDownloadButton" +
        "(Landroid/view/View;Ljava/lang/Object;Lcom/instagram/common/session/UserSession;Ljava/lang/Object;)V"

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTEGER_DESCRIPTOR = "Ljava/lang/Integer;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_EQUALS = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z"

/** The main feed module. The UFI variant selector is only consulted for this module. */
private const val MAIN_FEED_MODULE = "feed_timeline"

/** Node builder the UFI icon chain is made of. */
private const val COMPONENT_DESCRIPTOR = "LX/03iH;"

/** Compiled component the UFI icon chain resolves to. */
private const val COMPONENT_RESULT_DESCRIPTOR = "LX/03Wk;"

/** Kotlin event handler type the UFI `ON_CLICK` builder takes. */
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"

private const val WIDGET_SCALE_TYPE_DESCRIPTOR = "Landroid/widget/ImageView\$ScaleType;"

private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"

/** View class the UFI icon builder mounts. */
private const val VIEW_CLASS_BUTTON = "android.widget.Button"

private const val DOWNLOAD_CONTENT_DESCRIPTION = "Download"

/** Stock Instagram download glyph, also used by the save-media-comment patch. */
private const val DOWNLOAD_ICON_RESOURCE = "instagram_download_outline_24"

private const val COMPONENT_LIST_ADD_METHOD = "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z"

private const val INTEGER_VALUE_OF = "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;"

/** The Litho image wrapper the UFI icon builder passes its node through. */
private val ICON_WRAPPER_PARAMETERS =
    listOf(
        WIDGET_SCALE_TYPE_DESCRIPTOR,
        COMPONENT_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
        "I",
        "I",
    )

private const val CLICK_HANDLER_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/download/FeedDownloadClickFunction;"
private const val CLICK_HANDLER_CONSTRUCTOR =
    "$CLICK_HANDLER_DESCRIPTOR-><init>($CONTEXT_DESCRIPTOR$USER_SESSION_DESCRIPTOR$OBJECT_DESCRIPTOR)V"
private const val FEED_DOWNLOAD_ENABLED_METHOD =
    "$DOWNLOAD_DESCRIPTOR/DownloadUtils;->isFeedDownloadButtonEnabled()Z"

/** MobileConfig value that selects the classic view based UFI row. */
private const val VIEW_UFI_VARIANT = "view"

private const val LITHO_UFI_VARIANT = "litho"

private fun <T> requireOne(
    label: String,
    candidates: Collection<T>,
): T =
    if (candidates.size == 1) {
        candidates.single()
    } else {
        throw PatchException("Expected one $label, found ${candidates.size}: ${candidates.joinToString()}")
    }

/**
 * The feed post action row (like / comment / repost / share / save) is a plain view holder whose
 * constructor resolves `row_feed_button_save` with `requireViewById`. Matching that resource literal
 * finds the holder class without depending on obfuscated names. The root view is the only `View`
 * field assigned directly from the constructor's `View` parameter.
 */
private fun Method.resolveRootViewField(): FieldReference {
    val parameterRegister = registerOfParameter(VIEW_DESCRIPTOR)
    val instructions = implementation?.instructions?.toList() ?: emptyList()
    val candidates =
        instructions
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
                val twoRegister = instruction as TwoRegisterInstruction
                if (twoRegister.registerA != parameterRegister) return@mapNotNull null
                twoRegister.getReference<FieldReference>()?.takeIf { it.definingClass == definingClass }
            }.distinctBy { it.toString() }

    return requireOne("feed UFI root view field in $this", candidates)
}

private fun Method.parameterWords(): Int {
    var words = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    parameterTypes.forEach { words += if (it.toString() == "J" || it.toString() == "D") 2 else 1 }
    return words
}

private fun Method.parameterRegisterStart(): Int =
    (implementation?.registerCount ?: 0) - parameterWords()

private fun Method.registerOfParameter(descriptor: String): Int {
    var register = parameterRegisterStart() + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    parameterTypes.forEach { type ->
        val value = type.toString()
        if (value == descriptor) return register
        register += if (value == "J" || value == "D") 2 else 1
    }
    throw PatchException("Method $this has no $descriptor parameter")
}

/** One register word per parameter, in declaration order, paired with its smali type. */
private fun Method.parameterRegisters(): List<Pair<String, Int>> {
    var register = parameterRegisterStart() + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return parameterTypes.map { type ->
        val value = type.toString()
        val current = register
        register += if (value == "J" || value == "D") 2 else 1
        value to current
    }
}

private fun MethodReference.sameSignatureAs(other: MethodReference): Boolean =
    name == other.name &&
        returnType == other.returnType &&
        parameterTypes.map { it.toString() } == other.parameterTypes.map { it.toString() }

private fun BuilderInstruction.methodRef(): MethodReference? =
    (this as? ReferenceInstruction)?.reference as? MethodReference

/** Register words an invoke or move instruction reads, in operand order. */
private fun BuilderInstruction.registers(): List<Int> =
    when (this) {
        is FiveRegisterInstruction ->
            listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)

        is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
        is TwoRegisterInstruction -> listOf(registerA, registerB)
        is OneRegisterInstruction -> listOf(registerA)
        else -> emptyList()
    }

/** The UFI state object and its single `Media` field, resolved from the builder's `check-cast`. */
private class StateSource(
    val register: Int,
    val stateType: String,
    val mediaField: FieldReference,
)

/**
 * The feed row type decides how the UFI row is rendered:
 * `MEDIA_UFI` (view), `LITHO_MEDIA_UFI` or `COMPOSE_MEDIA_UFI`. Only the view renderer creates the
 * holder class this patch extends, so the main feed is pinned to `view`. The mock value is the
 * same `Integer` the selector would return for the `view` MobileConfig value, so the row type
 * contract is unchanged. Non feed modules keep the original selector result.
 */
private fun Method.resolveViewVariantField(): FieldReference {
    val instructions = implementation?.instructions?.toList() ?: emptyList()
    val viewStringIndex =
        instructions.indexOfFirst { instruction ->
            instruction.getReference<StringReference>()?.string == VIEW_UFI_VARIANT
        }
    if (viewStringIndex < 0) {
        throw PatchException("No \"$VIEW_UFI_VARIANT\" variant in UFI selector $this")
    }

    val candidates =
        instructions
            .drop(viewStringIndex + 1)
            .take(8)
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
                instruction.getReference<FieldReference>()?.takeIf { it.type == INTEGER_DESCRIPTOR }
            }.distinctBy { it.toString() }

    return requireOne("view UFI variant field in $this", candidates)
}

/**
 * Where the click handler's `UserSession` is read from inside the binder hook.
 *
 * [type] is the instance that owns [field]. [register] is the parameter (or `this`) register
 * holding that instance; the hook moves it into a scratch register before the `iget` because
 * format `22c` cannot address the argument registers of a method with many locals.
 */
private class UserSessionSource(
    val type: String,
    val register: Int,
    val field: FieldReference,
)

/**
 * Extension method whose two placeholder constants are rewritten with the live-index field names.
 * The click handlers call it at click time, so a carousel swipe after the bind is honored.
 */
private object CurrentMediaIndexFingerprint : Fingerprint(
    definingClass = "$DOWNLOAD_DESCRIPTOR/DownloadUtils;",
    name = "currentMediaIndex",
)

val feedDownloadButtonPatch =
    bytecodePatch(
        description = "Hooks the feed UFI row binder to add a download button beside the save icon.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            sharedExtensionPatch,
            // The download options dialog is an `InstagramDialogBox` wrapper. Without this
            // entity patch its placeholder class/method names are never resolved, so the
            // click handler dies in `addDialogMenuItems` with "Invoke failed: A0T".
            instagramDialogBoxEntity,
            mediaDataEntity,
            videoDataEntity,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            decoderEntity,
            resourceMappingPatch,
            // `settingsPatch` is what injects `SettingsStatus.load()` at app startup, declares
            // `FolderPickerActivity` in the manifest and adds the settings entry point. Without
            // it the download toggle, folder picker and filename options below never load.
            settingsPatch,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("instagram")

            // Only applied as a dependency of "Download media", which already enables the
            // download settings section. Flag this component's own settings toggle.
            enableSettings("feedDownloadButton")

            val saveButtonId = getResourceId(ResourceType.ID, "row_feed_button_save")

            val holderMatches =
                Fingerprint(
                    name = "<init>",
                    returnType = "V",
                    parameters = listOf(VIEW_DESCRIPTOR),
                    filters = listOf(literal(saveButtonId)),
                ).matchAll()

            if (holderMatches.size != 1) {
                throw PatchException(
                    "Expected one feed UFI row holder, found ${holderMatches.size}: " +
                        holderMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val holderMatch = holderMatches.single()
            val holderClass = holderMatch.classDef
            val holderDescriptor = holderClass.type
            val rootViewField = holderMatch.method.resolveRootViewField()

            // The feed item state is the holder field type that exposes exactly one Media.
            val stateType =
                requireOne(
                    "feed UFI media state type for $holderDescriptor",
                    holderClass.fields
                        .map { it.type }
                        .filter { type ->
                            classDefByOrNull(type)?.fields?.count { it.type == MEDIA_DESCRIPTOR } == 1
                        }.distinct(),
                )

            val stateClass: ClassDef = classDefBy(stateType)
            val mediaField = requireOne("Media field on $stateType", stateClass.fields.filter { it.type == MEDIA_DESCRIPTOR })

            // The row state walks to the live view state the carousel mutates. Its current media
            // index field is the same one the overflow-menu handler passes, so both feed download
            // entry points report the carousel item currently on screen. The extension reads both
            // fields at click time, after a swipe, instead of freezing the index at bind time.
            val viewStateField =
                requireOne(
                    "feed view state field on $stateType",
                    stateClass.fields.filter { it.type == MEDIA_ADD_INFO_CLASS_NAME },
                )

            // The click handlers resolve the live carousel index by walking the row state to the
            // view state the carousel mutates. These are the same two fields the overflow-menu
            // handler reads, injected into the extension so a swipe is honored instead of always
            // saving the first item.
            CurrentMediaIndexFingerprint.changeString("feedViewStateField", viewStateField.name)
            CurrentMediaIndexFingerprint.changeString("feedCurrentMediaField", CURRENT_MEDIA_FIELD.name)

            // The binder method receives both the holder and the media state.
            val bindCandidates = mutableListOf<Method>()
            classDefForEach { classDef ->
                classDef.methods.forEach { method ->
                    if (method.returnType != "V") return@forEach
                    val parameters = method.parameterTypes.map { it.toString() }
                    if (holderDescriptor in parameters && stateType in parameters) {
                        bindCandidates.add(method)
                    }
                }
            }

            val bindMethod = requireOne("feed UFI bind method", bindCandidates)

            val binderClass: ClassDef = classDefBy(bindMethod.definingClass)

            // 448 keeps the session in a binder field, while 439 passes it through the row state
            // parameter (`LX/00R5;->A4a`). Prefer the binder's own field, then the single parameter
            // whose type exposes exactly one UserSession field. Both shapes feed the same hook.
            val binderUserSessionFields = binderClass.fields.filter { it.type == USER_SESSION_DESCRIPTOR }
            if (binderUserSessionFields.size > 1) {
                throw PatchException(
                    "Expected at most one UserSession field on ${binderClass.type}, found " +
                        "${binderUserSessionFields.size}: ${binderUserSessionFields.joinToString()}",
                )
            }
            val userSessionSource =
                if (binderUserSessionFields.size == 1) {
                    UserSessionSource(
                        binderClass.type,
                        bindMethod.parameterRegisterStart(),
                        binderUserSessionFields.single(),
                    )
                } else {
                    requireOne(
                        "UserSession source for $bindMethod",
                        bindMethod.parameterRegisters().mapNotNull { (type, register) ->
                            val fields =
                                classDefByOrNull(type)?.fields?.filter { it.type == USER_SESSION_DESCRIPTOR }.orEmpty()
                            if (fields.size == 1) UserSessionSource(type, register, fields.single()) else null
                        },
                    )
                }

            val holderParameterRegister = bindMethod.registerOfParameter(holderDescriptor)
            val stateParameterRegister = bindMethod.registerOfParameter(stateType)
            val thisRegister = bindMethod.parameterRegisterStart()

            val mutableBind =
                mutableClassDefBy(bindMethod.definingClass).methods.first { it.sameSignatureAs(bindMethod) }

            mutableBind.insertHook(
                index = 0,
                excludedRegisters = (thisRegister until thisRegister + bindMethod.parameterWords()).toList(),
                relocateBranchTargets = false,
            ) {
                val holder = scratchRegister()
                move(holder, holderParameterRegister, holderDescriptor)
                val rootView = scratchRegister()
                iget(rootView, holder, rootViewField)

                val state = scratchRegister()
                move(state, stateParameterRegister, stateType)
                val media = scratchRegister()
                iget(media, state, mediaField)

                val sessionOwner = scratchRegister()
                move(sessionOwner, userSessionSource.register, userSessionSource.type)
                val userSession = scratchRegister()
                iget(userSession, sessionOwner, userSessionSource.field)

                invokeStatic(methodReference(EXTENSION_METHOD), rootView, media, userSession, state)
            }

            // The same feed post can render its UFI row as a view, a Litho component or a Compose
            // component, selected by a MobileConfig value. Only the view renderer creates the
            // feed row holder extended above, so pin the main feed to it. Modules other than the
            // main feed keep the original selector result. On 439 the selector is split into a
            // static variant used by `FeedItemBinderGroup` and an instance variant used by
            // `VowelBinderGroup`/`FeedFullHeightMediaBinderGroup`; both decide the same contract
            // and both are pinned.
            //
            // Forcing the view variant on the contextual profile modules (`feed_contextual_*`)
            // leaves the whole UFI row unbound there, so that surface keeps its original result.
            val variantSelectorMatches =
                Fingerprint(
                    returnType = INTEGER_DESCRIPTOR,
                    strings = listOf(MAIN_FEED_MODULE, LITHO_UFI_VARIANT, VIEW_UFI_VARIANT),
                ).matchAll()

            if (variantSelectorMatches.isEmpty() || variantSelectorMatches.size > 2) {
                throw PatchException(
                    "Expected one or two feed UFI variant selectors, found ${variantSelectorMatches.size}: " +
                        variantSelectorMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val viewVariantField =
                requireOne(
                    "view UFI variant field",
                    variantSelectorMatches.map { it.method.resolveViewVariantField() }.distinctBy { it.toString() },
                )

            variantSelectorMatches.forEach { selectorMatch ->
                val selectorMethod = selectorMatch.method
                val moduleRegister = selectorMethod.registerOfParameter(STRING_DESCRIPTOR)
                val selectorThisRegister = selectorMethod.parameterRegisterStart()

                val mutableSelector =
                    mutableClassDefBy(selectorMethod.definingClass)
                        .methods
                        .first { it.sameSignatureAs(selectorMethod) }

                mutableSelector.insertHook(
                    index = 0,
                    excludedRegisters =
                        (selectorThisRegister until selectorThisRegister + selectorMethod.parameterWords()).toList(),
                    relocateBranchTargets = false,
                ) {
                    val expectedModule = scratchRegister()
                    constString(expectedModule, MAIN_FEED_MODULE)
                    val isMainFeed = scratchRegister()
                    invokeVirtual(methodReference(STRING_EQUALS), moduleRegister, expectedModule)
                    moveResult(isMainFeed, "Z")
                    ifEqz(isMainFeed, Target.Original)

                    val variant = scratchRegister()
                    sget(variant, viewVariantField)
                    returnObject(variant)
                }
            }

            // `ContextualFeedFragment` (profile post viewer) renders its UFI as a Litho
            // component. The framework rejects a manual View added to that tree ("Adding Views
            // manually within LithoViews is not supported"), so the download icon becomes part
            // of the component itself: a second icon node is built into the UFI builder and
            // Litho mounts it like the save icon. The main feed's view holder keeps the runtime
            // insertion above; Litho surfaces get this injected component instead.
            //
            // The builder is located through the save-button resource literal. The icon wrapper,
            // event setter, component factory and icon list are all resolved from the
            // instructions that build the save icon, so no obfuscated name is hardcoded.
            val downloadDrawableId = getResourceId(ResourceType.DRAWABLE, DOWNLOAD_ICON_RESOURCE)

            val iconWrapperCtor =
                requireOne(
                    "feed UFI icon wrapper constructor",
                    buildList {
                        classDefForEach { classDef ->
                            classDef.methods.forEach { method ->
                                if (method.name == "<init>" &&
                                    method.parameterTypes.map { it.toString() } == ICON_WRAPPER_PARAMETERS
                                ) {
                                    add(method)
                                }
                            }
                        }
                    },
                )

            val builderMethod =
                requireOne(
                    "feed UFI litho component builder",
                    Fingerprint(filters = listOf(literal(saveButtonId)))
                        .matchAll()
                        .filter { match ->
                            match.method.implementation?.instructions?.any { instruction ->
                                instruction.methodRef()?.definingClass == iconWrapperCtor.definingClass
                            } == true
                        }.map { it.method },
                )

            val builderInstructions =
                builderMethod.implementation?.instructions?.toList()
                    ?: throw PatchException("Litho UFI builder $builderMethod has no implementation")

            val saveLiteralIndex =
                builderInstructions.indexOfFirst { instruction ->
                    instruction is Instruction31i && instruction.wideLiteral == saveButtonId
                }
            if (saveLiteralIndex < 0) throw PatchException("No save-button literal in $builderMethod")

            val idSetter =
                builderInstructions.drop(saveLiteralIndex + 1).mapNotNull { it.methodRef() }
                    .firstOrNull { reference ->
                        reference.returnType == COMPONENT_DESCRIPTOR &&
                            reference.parameterTypes.map { it.toString() } == listOf(COMPONENT_DESCRIPTOR, "I")
                    } ?: throw PatchException("No icon id setter after the save literal in $builderMethod")

            val viewClassIndex =
                (saveLiteralIndex - 1 downTo 0).firstOrNull { index ->
                    builderInstructions[index].getReference<StringReference>()?.string == VIEW_CLASS_BUTTON
                } ?: throw PatchException("No \"$VIEW_CLASS_BUTTON\" before the save literal in $builderMethod")
            val descriptionSetter =
                builderInstructions.getOrNull(viewClassIndex - 2)?.methodRef()
                    ?: throw PatchException("No content description setter before index $viewClassIndex in $builderMethod")

            val onClickSetter =
                builderInstructions.drop(saveLiteralIndex + 1).mapNotNull { it.methodRef() }
                    .firstOrNull { reference ->
                        reference.returnType == COMPONENT_DESCRIPTOR &&
                            reference.parameterTypes.map { it.toString() } ==
                            listOf(COMPONENT_DESCRIPTOR, FUNCTION1_DESCRIPTOR)
                    } ?: throw PatchException("No icon ON_CLICK setter after the save literal in $builderMethod")

            val onClickIndex =
                builderInstructions.indices.firstOrNull { index ->
                    index > saveLiteralIndex &&
                        builderInstructions[index].methodRef()?.sameSignatureAs(onClickSetter) == true
                } ?: throw PatchException("No ON_CLICK call after the save literal in $builderMethod")
            // The node handed to the save icon's ON_CLICK call has no click or long-click prop
            // yet; deriving the download node from it avoids inheriting the save behaviour.
            val nodeRegister =
                builderInstructions[onClickIndex].registers().firstOrNull()
                    ?: throw PatchException("ON_CLICK call at $onClickIndex has no node in $builderMethod")

            val wrapperCtorIndex =
                builderInstructions.indices.firstOrNull { index ->
                    index > onClickIndex && builderInstructions[index].methodRef()?.let { reference ->
                        reference.definingClass == iconWrapperCtor.definingClass && reference.name == "<init>"
                    } == true
                } ?: throw PatchException("No icon wrapper construction after the ON_CLICK call in $builderMethod")

            val componentCallIndex =
                builderInstructions.indices.firstOrNull { index ->
                    index > wrapperCtorIndex &&
                        builderInstructions[index].methodRef()?.returnType == COMPONENT_RESULT_DESCRIPTOR
                } ?: throw PatchException("No UFI button component factory call after the icon wrapper in $builderMethod")
            val componentFactory = builderInstructions[componentCallIndex].methodRef()!!
            val componentCallRegisters = builderInstructions[componentCallIndex].registers()
            if (componentCallRegisters.size != 7) {
                throw PatchException(
                    "Unexpected component factory call shape in $builderMethod: " +
                        "${componentCallRegisters.size} registers",
                )
            }

            // The five component arguments are staged into the call registers by the moves
            // right before it. After the save icon's call those registers are dead, so the
            // download icon reuses the same consecutive range to build its wrapper without
            // reserving a scratch span.
            val componentSourceRegisters =
                componentCallRegisters.drop(2).map { destination ->
                    (componentCallIndex - 1 downTo (componentCallIndex - 8).coerceAtLeast(0))
                        .firstNotNullOfOrNull { index ->
                            (builderInstructions[index] as? TwoRegisterInstruction)
                                ?.takeIf { it.registerA == destination }
                                ?.registerB
                        } ?: throw PatchException(
                        "No source move for UFI component argument v$destination in $builderMethod",
                    )
                }

            val listAddIndex =
                (componentCallIndex + 1 until builderInstructions.size).firstOrNull { index ->
                    val reference = builderInstructions[index].methodRef() ?: return@firstOrNull false
                    reference.name == "add" &&
                        reference.parameterTypes.map { it.toString() } == listOf(OBJECT_DESCRIPTOR) &&
                        reference.returnType == "Z"
                } ?: throw PatchException("No UFI icon list add after the component factory call in $builderMethod")
            val listRegister =
                builderInstructions[listAddIndex].registers().firstOrNull()
                    ?: throw PatchException("UFI icon list add at $listAddIndex has no receiver in $builderMethod")

            val stateRegister =
                builderInstructions.firstOrNull { instruction ->
                    instruction.opcode == Opcode.CHECK_CAST &&
                        instruction.getReference<TypeReference>()?.type == stateType
                }?.let { (it as OneRegisterInstruction).registerA }
                    ?: throw PatchException("No check-cast to $stateType in $builderMethod")

            // `01kO.A05(componentContext, attr)` resolves a theme attribute to an int. The save
            // icon reads its resolved size and tint right before the wrapper is built; those two
            // lookups supply the injected wrapper with the same values without an `iget` from
            // high registers (22c only encodes 4-bit registers).
            val themeAccessorCalls =
                (saveLiteralIndex until componentCallIndex).mapNotNull { index ->
                    val instruction = builderInstructions[index]
                    val reference = instruction.methodRef() ?: return@mapNotNull null
                    val parameters = reference.parameterTypes.map { it.toString() }
                    if (instruction.opcode != Opcode.INVOKE_STATIC ||
                        reference.returnType != "I" ||
                        parameters.size != 2 ||
                        parameters[1] != "I"
                    ) {
                        return@mapNotNull null
                    }
                    index to reference
                }
            if (themeAccessorCalls.size != 2 ||
                !themeAccessorCalls[0].second.sameSignatureAs(themeAccessorCalls[1].second)
            ) {
                throw PatchException(
                    "Expected two matching theme attribute lookups before the icon wrapper in " +
                        "$builderMethod, found ${themeAccessorCalls.size}",
                )
            }
            val themeAccessor = themeAccessorCalls[0].second

            fun attributeLiteral(callIndex: Int): Int {
                for (index in callIndex - 1 downTo (callIndex - 4).coerceAtLeast(0)) {
                    val literal = builderInstructions[index] as? Instruction31i ?: continue
                    return literal.wideLiteral.toInt()
                }
                throw PatchException("No theme attribute constant before index $callIndex in $builderMethod")
            }

            val dimensionAttribute = attributeLiteral(themeAccessorCalls[0].first)
            val tintAttribute = attributeLiteral(themeAccessorCalls[1].first)

            // `A0o`'s only parameter is the component context (`LX/01iy`, an `LX/0AsI` that
            // extends `LX/0mwk`), still intact at the injection point.
            val componentContextRegister = builderMethod.parameterRegisters().single().second
            val componentContextType = themeAccessor.parameterTypes[0].toString()
            val contextGetter =
                requireOne(
                    "component context accessor",
                    classDefBy(componentContextType).methods.filter { method ->
                        method.parameterTypes.isEmpty() && method.returnType == CONTEXT_DESCRIPTOR
                    },
                )

            val excludedRegisters =
                buildList {
                    addAll(
                        (builderMethod.parameterRegisterStart() until
                            builderMethod.parameterRegisterStart() + builderMethod.parameterWords()).toList(),
                    )
                    add(nodeRegister)
                    add(stateRegister)
                    add(listRegister)
                    addAll(componentCallRegisters)
                    addAll(componentSourceRegisters)
                }.distinct()

            val mutableBuilder =
                mutableClassDefBy(builderMethod.definingClass).methods.first { it.sameSignatureAs(builderMethod) }

            // Runs in front of the save icon's ON_CLICK call, where the passed node is still
            // free of the save handlers. The save wrapper construction right after this point
            // re-initialises its own `v39`..`v44` range, so the download wrapper and factory
            // call reuse that consecutive range and every invoke stays span-free.
            mutableBuilder.insertHook(
                index = onClickIndex,
                excludedRegisters = excludedRegisters,
                relocateBranchTargets = false,
            ) {
                val enabled = scratchRegister(RegisterLimit.BYTE)
                invokeStatic(methodReference(FEED_DOWNLOAD_ENABLED_METHOD))
                moveResult(enabled, "Z")
                ifEqz(enabled, Target.Local("skipFeedDownloadButton"))

                // Every invoke is staged through the save icon's consecutive `v39`..`v44`
                // range. The save rebuilds that range right after this point, so the register
                // allocator never has to find a scratch span in this dense method.
                val stage = componentCallRegisters.drop(1)

                val clickHandler = scratchRegister(RegisterLimit.BYTE)
                newInstance(clickHandler, CLICK_HANDLER_DESCRIPTOR)
                val androidContext = scratchRegister(RegisterLimit.BYTE)
                invokeInterface(contextGetter, componentContextRegister)
                moveResult(androidContext, CONTEXT_DESCRIPTOR)
                move(stage[0], clickHandler, CLICK_HANDLER_DESCRIPTOR)
                move(stage[1], androidContext, CONTEXT_DESCRIPTOR)
                move(stage[2], componentSourceRegisters[2], OBJECT_DESCRIPTOR)
                move(stage[3], stateRegister, OBJECT_DESCRIPTOR)
                invokeDirect(
                    methodReference(CLICK_HANDLER_CONSTRUCTOR),
                    stage[0],
                    stage[1],
                    stage[2],
                    stage[3],
                )

                val value = scratchRegister(RegisterLimit.BYTE)
                val downloadNode = scratchRegister(RegisterLimit.BYTE)
                constInt(value, -1)
                move(stage[0], nodeRegister, OBJECT_DESCRIPTOR)
                move(stage[1], value, "I")
                invokeStatic(idSetter, stage[0], stage[1])
                moveResult(downloadNode, COMPONENT_DESCRIPTOR)

                constString(value, DOWNLOAD_CONTENT_DESCRIPTION)
                move(stage[0], downloadNode, OBJECT_DESCRIPTOR)
                move(stage[1], value, OBJECT_DESCRIPTOR)
                invokeStatic(descriptionSetter, stage[0], stage[1])
                moveResult(downloadNode, COMPONENT_DESCRIPTOR)

                move(stage[0], downloadNode, OBJECT_DESCRIPTOR)
                move(stage[1], clickHandler, OBJECT_DESCRIPTOR)
                invokeStatic(onClickSetter, stage[0], stage[1])
                moveResult(downloadNode, COMPONENT_DESCRIPTOR)

                val scaleType = scratchRegister(RegisterLimit.BYTE)
                sget(
                    scaleType,
                    fieldReference("Landroid/widget/ImageView\$ScaleType;->CENTER:Landroid/widget/ImageView\$ScaleType;"),
                )

                constInt(value, dimensionAttribute)
                move(stage[0], componentContextRegister, OBJECT_DESCRIPTOR)
                move(stage[1], value, "I")
                invokeStatic(themeAccessor, stage[0], stage[1])
                val dimension = scratchRegister(RegisterLimit.BYTE)
                moveResult(dimension, "I")

                constInt(value, tintAttribute)
                move(stage[0], componentContextRegister, OBJECT_DESCRIPTOR)
                move(stage[1], value, "I")
                invokeStatic(themeAccessor, stage[0], stage[1])
                val tintColor = scratchRegister(RegisterLimit.BYTE)
                moveResult(tintColor, "I")
                invokeStatic(methodReference(INTEGER_VALUE_OF), tintColor)
                val tint = scratchRegister(RegisterLimit.BYTE)
                moveResult(tint, "Ljava/lang/Integer;")

                constInt(value, downloadDrawableId.toInt())
                newInstance(componentCallRegisters[1], iconWrapperCtor.definingClass)
                move(componentCallRegisters[2], scaleType, OBJECT_DESCRIPTOR)
                move(componentCallRegisters[3], downloadNode, OBJECT_DESCRIPTOR)
                move(componentCallRegisters[4], tint, OBJECT_DESCRIPTOR)
                move(componentCallRegisters[5], value, "I")
                move(componentCallRegisters[6], dimension, "I")
                invokeDirect(
                    iconWrapperCtor,
                    componentCallRegisters[1],
                    componentCallRegisters[2],
                    componentCallRegisters[3],
                    componentCallRegisters[4],
                    componentCallRegisters[5],
                    componentCallRegisters[6],
                )

                move(componentCallRegisters[2], componentSourceRegisters[0], OBJECT_DESCRIPTOR)
                move(componentCallRegisters[3], componentSourceRegisters[1], OBJECT_DESCRIPTOR)
                move(componentCallRegisters[4], componentSourceRegisters[2], OBJECT_DESCRIPTOR)
                move(componentCallRegisters[5], componentSourceRegisters[3], "Z")
                move(componentCallRegisters[6], componentSourceRegisters[4], "Z")
                invokeStatic(
                    componentFactory,
                    componentCallRegisters[0],
                    componentCallRegisters[1],
                    componentCallRegisters[2],
                    componentCallRegisters[3],
                    componentCallRegisters[4],
                    componentCallRegisters[5],
                    componentCallRegisters[6],
                )
                val component = scratchRegister(RegisterLimit.BYTE)
                moveResult(component, COMPONENT_RESULT_DESCRIPTOR)
                move(stage[0], listRegister, OBJECT_DESCRIPTOR)
                move(stage[1], component, OBJECT_DESCRIPTOR)
                invokeVirtual(methodReference(COMPONENT_LIST_ADD_METHOD), stage[0], stage[1])
                label("skipFeedDownloadButton")
            }
        }
    }
