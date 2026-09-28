/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
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
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val VIEW_DESCRIPTOR = "Landroid/view/View;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"
private const val USER_SESSION_DESCRIPTOR = "Lcom/instagram/common/session/UserSession;"
private const val EXTENSION_METHOD =
    "$DOWNLOAD_DESCRIPTOR/DownloadUtils;->addFeedDownloadButton" +
        "(Landroid/view/View;Ljava/lang/Object;Lcom/instagram/common/session/UserSession;)V"

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTEGER_DESCRIPTOR = "Ljava/lang/Integer;"
private const val STRING_EQUALS = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z"

/** The main feed module. The UFI variant selector is only consulted for this module. */
private const val MAIN_FEED_MODULE = "feed_timeline"

/** MobileConfig value that selects the classic view based UFI row. */
private const val VIEW_UFI_VARIANT = "view"

private const val LITHO_UFI_VARIANT = "litho"

/** Profile post surfaces that render the same view based UFI row as the main feed. */
private val PROFILE_MODULES =
    listOf(
        "feed_contextual_self_profile",
        "feed_contextual_profile",
        "feed_contextual_group_profile",
    )

private const val PROFILE_VIEW_LABEL = "piko_profile_view"

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

private fun Method.sameSignatureAs(other: Method): Boolean =
    name == other.name &&
        returnType == other.returnType &&
        parameterTypes.map { it.toString() } == other.parameterTypes.map { it.toString() }

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

                invokeStatic(methodReference(EXTENSION_METHOD), rootView, media, userSession)
            }

            // The same feed post can render its UFI row as a view, a Litho component or a Compose
            // component, selected by a MobileConfig value. Only the view renderer creates the
            // feed row holder extended above, so pin the main feed to it. Modules other than the
            // main feed keep the original selector result. On 439 the selector is split into a
            // static variant used by `FeedItemBinderGroup` and an instance variant used by
            // `VowelBinderGroup`/`FeedFullHeightMediaBinderGroup`; both decide the same contract
            // and both are pinned.
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

            // Profile posts choose their UFI variant through a profile module chooser instead of
            // the feed selector above (`LX/00SX` returns null for non-feed modules). Pin the
            // profile module set to the same view variant so the row holder extended above is
            // also created on profile post viewers.
            val profileSelectorMatches =
                Fingerprint(
                    returnType = INTEGER_DESCRIPTOR,
                    strings = PROFILE_MODULES,
                ).matchAll()

            if (profileSelectorMatches.isEmpty() || profileSelectorMatches.size > 2) {
                throw PatchException(
                    "Expected one or two profile UFI variant selectors, found ${profileSelectorMatches.size}: " +
                        profileSelectorMatches.joinToString { it.originalMethod.toString() },
                )
            }

            profileSelectorMatches.forEach { selectorMatch ->
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
                    // The module parameter sits above v15, so copy it into a low scratch register
                    // and keep the comparison at 35c instead of forcing a /range invoke, which
                    // would need a contiguous scratch span this method does not have.
                    val module = scratchRegister()
                    move(module, moduleRegister, STRING_DESCRIPTOR)
                    val comparison = scratchRegister()
                    PROFILE_MODULES.forEach { profileModule ->
                        constString(comparison, profileModule)
                        invokeVirtual(methodReference(STRING_EQUALS), module, comparison)
                        moveResult(comparison, "Z")
                        ifNez(comparison, Target.Local(PROFILE_VIEW_LABEL))
                    }
                    goto(Target.Original)

                    label(PROFILE_VIEW_LABEL)
                    sget(comparison, viewVariantField)
                    returnObject(comparison)
                }
            }
        }
    }
