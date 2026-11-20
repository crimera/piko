/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.feedWhitelist

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.reelResponseItem.reelResponseItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.filter.story.PopulateStoryTrayFingerprint
import app.crimera.patches.instagram.misc.distractionFree.limitFeedToFollowingProfiles
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val FEED_DESCRIPTOR = "$PATCHES_DESCRIPTOR/feed"
private const val WHITELIST_CLASS = "$FEED_DESCRIPTOR/FeedWhitelist;"
private const val FETCHER_CLASS = "$FEED_DESCRIPTOR/FollowingFetcher;"
private const val CALLBACK_CLASS = "$FEED_DESCRIPTOR/FollowingRequestCallback;"

/** Replaces an extension stub with [smali], using as many registers as it needs. */
private fun replaceStub(classType: String, name: String, registers: Int, smali: String) {
    val extension = mutableClassDefBy(classType)
    val stub = extension.methods.singleOrNull { it.name == name }
        ?: throw PatchException("Missing extension stub $classType->$name")
    extension.methods.remove(stub)
    extension.methods.add(
        ImmutableMethod(
            classType,
            stub.name,
            stub.parameters,
            stub.returnType,
            stub.accessFlags,
            null,
            null,
            MutableMethodImplementation(registers),
        ).toMutable().apply { addInstructions(0, smali.trimIndent()) },
    )
}

private fun Method.firstReference(opcode: Opcode) =
    implementation?.instructions?.firstOrNull { it.opcode == opcode }

/** A no-argument getter, guarded so other objects return null. */
private fun guardedGetter(receiverType: String, getter: String) =
    """
    instance-of v0, p0, $receiverType
    if-eqz v0, :piko_none
    check-cast p0, $receiverType
    invoke-virtual {p0}, $getter
    move-result-object p0
    return-object p0
    :piko_none
    const/4 p0, 0x0
    return-object p0
    """

@Suppress("unused")
val whitelistFollowedAccountsPatch =
    patch(
        name = "Whitelist followed accounts",
        description = "Adds a menu under \"Limit feed to following profiles\" to pick followed accounts " +
            "whose home feed posts and stories are the only ones shown, or are hidden.",
    ) {
        // The menu sits under the "Limit feed to following profiles" switch, but works without it.
        dependsOn(settingsPatch, limitFeedToFollowingProfiles, mediaDataEntity, userDataEntity, reelResponseItemEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // Media of a home feed item.
            val itemType = MainFeedResponseParseFingerprint.instructionMatches[1].methodReference.returnType
            val mediaGetter = classDefBy(itemType).methods.singleOrNull { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.isEmpty() && method.returnType == MEDIA_CLASS_NAME
            } ?: throw PatchException("Could not resolve the media getter of $itemType")
            replaceStub(WHITELIST_CLASS, "getFeedItemMedia", 2, guardedGetter(itemType, mediaGetter.toString()))

            // Filter what enters the home feed store. On launch the feed comes from the on-device
            // cache, which never passes through the network response parser.
            ListStoreInsertFingerprint.method.apply {
                val storeBase = definingClass
                val mainFeedStore = MainFeedStoreFingerprint.instructionMatches.last()
                    .getInstruction<ReferenceInstruction>().getReference<TypeReference>()!!.type
                if (generateSequence(mainFeedStore) { classDefBy(it).superclass }.none { it == storeBase }) {
                    throw PatchException("$mainFeedStore is not a $storeBase list store")
                }

                addInstructionsWithLabels(
                    0,
                    """
                    instance-of v0, p0, $mainFeedStore
                    if-eqz v0, :piko_keep
                    invoke-static { p2 }, $WHITELIST_CLASS->isAllowedInFeed(Ljava/lang/Object;)Z
                    move-result v0
                    if-nez v0, :piko_keep
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_keep", getInstruction(0)),
                )
            }

            // Home story tray, after decoding. The signed-in user is passed so their own story stays.
            PopulateStoryTrayFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range { p3 .. p5 }, $WHITELIST_CLASS->filterStoryTray(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;)Ljava/util/List;
                move-result-object p5
                """.trimIndent(),
            )

            // The Following list request, its callback and the way Instagram runs a request inline.
            val userListRequest = UserListRequestFingerprint.method
            val requestType = userListRequest.returnType
            val parserType = UserListRequestFingerprint.instructionMatches[1].fieldReference.type

            val setCallback = InlineProfileRequestFingerprint.instructionMatches[1].methodReference
            val runInline = InlineProfileRequestFingerprint.instructionMatches[2].methodReference
            val callbackType = setCallback.parameterTypes.single().toString()

            val onSuccess = requestFinishFingerprint(requestType, callbackType)
                .instructionMatches.last().methodReference.name

            mutableClassDefBy(CALLBACK_CLASS).apply {
                setSuperClass(callbackType)
                (methods.singleOrNull { it.name == "onRequestSuccess" }
                    ?: throw PatchException("Missing extension callback method")).name = onSuccess
            }

            replaceStub(
                FETCHER_CLASS,
                "executeFollowingRequest",
                16,
                """
                move-object v0, p0
                move-object v1, p1
                const/4 v2, 0x0
                const/4 v3, 0x0
                move-object v4, p2
                const/4 v5, 0x0
                const/4 v6, 0x0
                const/4 v7, 0x0
                const/4 v8, 0x0
                const/4 v9, 0x0
                const/4 v10, 0x0
                const/4 v11, 0x0
                invoke-static/range { v0 .. v11 }, $userListRequest
                move-result-object v0
                invoke-virtual { v0, p3 }, $requestType->${setCallback.name}($callbackType)V
                invoke-static { v0 }, $runInline
                return-void
                """,
            )

            // Users and next page cursor of the user list response.
            val parseMethod = classDefBy(parserType).methods.singleOrNull { it.name == "unsafeParseFromJson" }
                ?: throw PatchException("Could not resolve the user list response parser")
            val responseType = parseMethod.implementation!!.instructions
                .last { it.opcode == Opcode.NEW_INSTANCE }.getReference<TypeReference>()!!.type
            val responseClass = classDefBy(responseType)
            val itemsGetter = responseClass.methods.singleOrNull {
                it.name == "getItems" && it.parameterTypes.isEmpty()
            } ?: throw PatchException("Could not resolve the user list response items")
            val responseInterface = itemsGetter.firstReference(Opcode.CHECK_CAST)
                ?.getReference<TypeReference>()?.type
                ?: throw PatchException("Could not resolve the user list response interface")

            val nextMaxIdName = userListNextMaxIdFingerprint(responseInterface).originalMethod.name
            val nextMaxIdGetter = responseClass.methods.singleOrNull { method ->
                method.parameterTypes.isEmpty() && method.returnType == "Ljava/lang/String;" &&
                    method.implementation?.instructions?.any { instruction ->
                        instruction.getReference<MethodReference>()?.let {
                            it.definingClass == responseInterface && it.name == nextMaxIdName
                        } == true
                    } == true
            } ?: throw PatchException("Could not resolve the user list next page cursor")

            replaceStub(FETCHER_CLASS, "getResponseUsers", 2, guardedGetter(responseType, itemsGetter.toString()))
            replaceStub(FETCHER_CLASS, "getResponseNextMaxId", 2, guardedGetter(responseType, nextMaxIdGetter.toString()))

            // The signed-in session of the Piko settings activity.
            val sessionGetter = ModalActivitySessionFingerprint.instructionMatches.first().methodReference
            replaceStub(
                FETCHER_CLASS,
                "getActivitySession",
                2,
                guardedGetter(IG_FRAGMENT_ACTIVITY_CLASS, sessionGetter.toString()),
            )

            enableSettings("feedWhitelist")
        }
    }
