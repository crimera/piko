package app.crimera.patches.newx.misc.postoptions

import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.MultiChoiceSettingDefinition
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.POST_OPTIONS_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val POST_OPTIONS_STATE_PREFIX = "PostOptionsState(showOptionsDialog="
private const val POST_OPTIONS_LIST_PREFIX = ", options="
private const val LIST_DESCRIPTOR = "Ljava/util/List;"

private object CustomizePostOptionsStateFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string(POST_OPTIONS_STATE_PREFIX),
            app.morphe.patcher.string(POST_OPTIONS_LIST_PREFIX),
        ),
)

private fun stateConstructorParameters() =
    listOf(
        "Z",
        "L",
        LIST_DESCRIPTOR,
        "Ljava/util/Map;",
        "Lkotlinx/coroutines/flow/",
        "Lkotlin/jvm/functions/Function1;",
        "L",
        "L",
    )

// Every hidden ID must resolve to a real PostActionType entry. Aliased entries are
// listed so renames fail closed at patch time instead of silently keeping the row.
private val HIDDEN_ID_TO_ENUM_NAMES =
    mapOf(
        "CopyLinkToTweet" to listOf("CopyLinkToTweet"),
        "ShareViaDM" to listOf("ShareViaDM"),
        "Share" to listOf("Share", "TwitterShare", "PromotedShareVia"),
        "AddToBookmarks" to listOf("AddToBookmarks", "RemoveFromBookmarks"),
        "Follow" to listOf("Follow", "Unfollow"),
        "Mute" to listOf("Mute", "Unmute"),
        "Block" to listOf("Block", "Unblock"),
        "Report" to listOf("Report", "ReportDsa"),
        "IDontLikeThisTweet" to
            listOf("IDontLikeThisTweet", "SeeFewer", "NotRelevant", "NotCredible", "NotAboutTopic"),
        "MuteConversation" to listOf("MuteConversation", "UnmuteConversation"),
        "Pin" to listOf("Pin", "Unpin", "PinReply", "UnpinReply"),
        "Delete" to listOf("Delete"),
        "Edit" to listOf("Edit", "EditUnavailable", "EditWithTwitterBlue"),
        "Quote" to listOf("Quote"),
        "Retweet" to listOf("Retweet", "UndoRetweet"),
        "Favorite" to listOf("Favorite", "Unfavorite"),
        "Dislike" to listOf("Dislike", "UndoDislike"),
        "ViewTweetAnalytics" to listOf("ViewTweetAnalytics"),
        "ChangeConversationControl" to listOf("ChangeConversationControl"),
        "AddRemoveFromList" to listOf("AddRemoveFromList"),
        "RequestCommunityNote" to listOf("RequestCommunityNote", "ContributeToBirdwatch"),
        "BoostPost" to listOf("BoostPost", "BoostPostAgain"),
        "ToggleHighlight" to listOf("ToggleHighlight", "AddHighlight", "RemoveHighlight"),
    )

@Suppress("unused")
val customizeNewXPostOptionsPatch =
    bytecodePatch(
        name = "NewX: Customize post menu items",
        description = "Lets you hide selected items from the NewX post menu.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXInlineActionModelResolutionPatch)

        val hiddenItems =
            newXMultiChoice(
                id = "newx.content.hidden_post_options",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_post_options"),
                order = 150,
                defaultValue = emptySet(),
                options =
                    listOf(
                        choice("CopyLinkToTweet", "piko_newx_post_option_copy_link"),
                        choice("ShareViaDM", "piko_newx_post_option_share_via_dm"),
                        choice("Share", "piko_newx_post_option_share"),
                        choice("AddToBookmarks", "piko_newx_post_option_bookmark"),
                        choice("Follow", "piko_newx_post_option_follow"),
                        choice("Mute", "piko_newx_post_option_mute"),
                        choice("Block", "piko_newx_post_option_block"),
                        choice("Report", "piko_newx_post_option_report"),
                        choice("IDontLikeThisTweet", "piko_newx_post_option_not_interested"),
                        choice("MuteConversation", "piko_newx_post_option_mute_conversation"),
                        choice("Pin", "piko_newx_post_option_pin"),
                        choice("Delete", "piko_newx_post_option_delete"),
                        choice("Edit", "piko_newx_post_option_edit"),
                        choice("Quote", "piko_newx_post_option_quote"),
                        choice("Retweet", "piko_newx_post_option_repost"),
                        choice("Favorite", "piko_newx_post_option_like"),
                        choice("Dislike", "piko_newx_post_option_dislike"),
                        choice("ViewTweetAnalytics", "piko_newx_post_option_view_analytics"),
                        choice("ChangeConversationControl", "piko_newx_post_option_change_reply_permission"),
                        choice("AddRemoveFromList", "piko_newx_post_option_add_to_list"),
                        choice("RequestCommunityNote", "piko_newx_post_option_community_note"),
                        choice("BoostPost", "piko_newx_post_option_boost"),
                        choice("ToggleHighlight", "piko_newx_post_option_highlight"),
                    ),
            )

        execute {
            validateHiddenEnums()
            injectPostOptionsFilter(hiddenItems)
        }
    }

context(context: BytecodePatchContext)
private fun validateHiddenEnums() {
    val postActionType = resolvedNewXInlineActionModels().postActionTypeDescriptor
    val actionClass = context.classDefByOrNull(postActionType)
        ?: throw PatchException("NewX post action type class was not found: $postActionType")
    val entries = actionClass.fields
        .filter { AccessFlags.STATIC.isSet(it.accessFlags) && it.type == postActionType }
        .map { it.name }
        .toSet()
    val missing = HIDDEN_ID_TO_ENUM_NAMES.values.flatten().filter { it !in entries }
    if (missing.isNotEmpty()) {
        throw PatchException("Missing NewX post-menu actions: ${missing.joinToString()}")
    }
}

context(context: BytecodePatchContext)
private fun injectPostOptionsFilter(hiddenItems: MultiChoiceSettingDefinition) {
    val stateMatch = requireExactlyOne(
        "NewX post-options state",
        CustomizePostOptionsStateFingerprint.scopedMatchAll().toList(),
    )
    val stateType = stateMatch.originalClassDef.type
    val constructorMatch = requireExactlyOne(
        "NewX post-options state constructor",
        Fingerprint(
            classFingerprint = CustomizePostOptionsStateFingerprint,
            name = "<init>",
            returnType = "V",
            parameters = stateConstructorParameters(),
        ).scopedMatchAll().toList(),
    )
    check(constructorMatch.originalClassDef.type == stateType) {
        "NewX post-options state constructor owner changed: ${constructorMatch.originalMethod}"
    }

    val mutableClass = context.mutableClassDefBy(stateType)
    val method = constructorMatch.method.cloneMutable(additionalRegisters = 2)
    mutableClass.methods.remove(constructorMatch.method)
    mutableClass.methods.add(method)
    val listStoreIndex = method.postOptionsListStoreIndex(stateType)
    // Never assume p-names map to constructor params: cloning and earlier injections
    // can shuffle params into moves, so derive the options register from the store itself.
    val optionsRegister = method.postOptionsListValueRegister(listStoreIndex)
    if (optionsRegister !in 0..15) {
        throw PatchException(
            "NewX post-options list register is not 4-bit: v$optionsRegister",
        )
    }
    val read = try {
        hiddenItems.injectRead(
            method = method,
            index = listStoreIndex,
            excludedRegisters = listOf(optionsRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    } catch (exception: RuntimeException) {
        throw PatchException(
            "Failed to inject the NewX hidden post-menu setting read: " +
                exception.message,
        )
    }

    method.addInstructions(
        read.nextIndex,
        """
            invoke-static {v$optionsRegister, v${read.register}}, $POST_OPTIONS_FILTER_DESCRIPTOR->filter(Ljava/util/List;Ljava/util/Set;)Ljava/util/List;
            move-result-object v$optionsRegister
        """.trimIndent(),
    )
}

private fun MutableMethod.postOptionsListValueRegister(storeIndex: Int): Int {
    val store = instructions.getOrNull(storeIndex)
        ?: throw PatchException("NewX post-options list store has no instruction at $storeIndex")
    if (store.opcode != Opcode.IPUT_OBJECT) {
        throw PatchException("NewX post-options list store is not an iput-object: $store")
    }
    return (store as? TwoRegisterInstruction)?.registerA
        ?: throw PatchException("NewX post-options list store has no value register: $store")
}

private fun MutableMethod.postOptionsListStoreIndex(stateType: String): Int {
    val candidates = instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapIndexedNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        if (field.definingClass != stateType || field.type != LIST_DESCRIPTOR) {
            return@mapIndexedNotNull null
        }
        index
    }
    return requireExactlyOne("NewX post-options list store", candidates)
}
