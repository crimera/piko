package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.models.smaliReference
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INTEGER_DESCRIPTOR = "I"
private const val POST_ENTITY_LIST_DESCRIPTOR = "Lcom/x/models/text/PostEntityList;"
private const val MENTION_DESCRIPTOR = "Lcom/x/models/text/MentionEntity;"
private const val USER_RESULT_DESCRIPTOR = "Lcom/x/models/UserResult;"
private const val X_USER_DESCRIPTOR = "Lcom/x/models/XUser;"

/** Post text/mention/author models resolved for the post-filter keyword bridges. */
private data class ResolvedPostTextModels(
    val postEntityListGetter: MethodReference,
    val postEntityListGetMentions: MethodReference,
    val mentionStartIdxGetter: MethodReference,
    val mentionEndIdxGetter: MethodReference,
    val mentionScreenNameGetter: MethodReference,
    val postAuthorGetter: MethodReference,
    val userScreenNameGetter: MethodReference,
)

internal val newXTimelineTextModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(newXTimelineModelAdapterPatch)

        execute {
            val models = resolvedNewXTimelineModels()
            patchPostTextBridges(
                postDescriptor = models.postDescriptor,
                textGetter = models.postTextGetter,
                postTextModels = resolvePostTextModels(models.postDescriptor),
            )
        }
    }

context(context: BytecodePatchContext)
private fun resolvePostTextModels(postDescriptor: String): ResolvedPostTextModels {
    val postClass = context.mutableClassDefBy(postDescriptor)
    val postEntityListGetter = postClass.methods.singleOrNull { method ->
        method.name == "getEntityList" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == POST_ENTITY_LIST_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX timeline post getEntityList() in $postClass: " +
            postClass.methods.joinToString(),
    )
    val postAuthorGetter = postClass.methods.singleOrNull { method ->
        method.name == "getAuthor" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == USER_RESULT_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX timeline post getAuthor() in $postClass: " +
            postClass.methods.joinToString(),
    )

    val postEntityListClass = context.mutableClassDefBy(POST_ENTITY_LIST_DESCRIPTOR)
    val postEntityListGetMentions = postEntityListClass.methods.singleOrNull { method ->
        method.name == "getMentions" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == LIST_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX post-entity-list getMentions() in $postEntityListClass: " +
            postEntityListClass.methods.joinToString(),
    )

    val mentionClass = context.mutableClassDefBy(MENTION_DESCRIPTOR)
    val mentionStartIdxGetter = mentionClass.methods.singleOrNull { method ->
        method.name == "getStartIdx" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == INTEGER_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX mention getStartIdx() in $mentionClass: " +
            mentionClass.methods.joinToString(),
    )
    val mentionEndIdxGetter = mentionClass.methods.singleOrNull { method ->
        method.name == "getEndIdx" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == INTEGER_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX mention getEndIdx() in $mentionClass: " +
            mentionClass.methods.joinToString(),
    )
    val mentionScreenNameGetter = mentionClass.methods.singleOrNull { method ->
        method.name == "getScreenName" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX mention getScreenName() in $mentionClass: " +
            mentionClass.methods.joinToString(),
    )

    val xUserClass = context.mutableClassDefBy(X_USER_DESCRIPTOR)
    val userScreenNameGetter = xUserClass.methods.singleOrNull { method ->
        method.name == "getScreenName" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX user getScreenName() in $xUserClass: " +
            xUserClass.methods.joinToString(),
    )

    return ResolvedPostTextModels(
        postEntityListGetter = postEntityListGetter,
        postEntityListGetMentions = postEntityListGetMentions,
        mentionStartIdxGetter = mentionStartIdxGetter,
        mentionEndIdxGetter = mentionEndIdxGetter,
        mentionScreenNameGetter = mentionScreenNameGetter,
        postAuthorGetter = postAuthorGetter,
        userScreenNameGetter = userScreenNameGetter,
    )
}

context(context: BytecodePatchContext)
private fun patchPostTextBridges(
    postDescriptor: String,
    textGetter: MethodReference,
    postTextModels: ResolvedPostTextModels,
) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
    filterClass.patchBridge(
        "getPostText",
        OBJECT_DESCRIPTOR,
        STRING_DESCRIPTOR,
        """
            check-cast p0, $postDescriptor
            invoke-virtual {p0}, ${textGetter.smaliReference()}
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getPostMentions",
        OBJECT_DESCRIPTOR,
        LIST_DESCRIPTOR,
        """
            check-cast p0, $postDescriptor
            invoke-virtual {p0}, ${postTextModels.postEntityListGetter.smaliReference()}
            move-result-object p0
            if-eqz p0, :piko_newx_post_mentions_null
            invoke-virtual {p0}, ${postTextModels.postEntityListGetMentions.smaliReference()}
            move-result-object p0
            return-object p0
            :piko_newx_post_mentions_null
            const/4 p0, 0x0
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionStartIdx",
        OBJECT_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
        """
            check-cast p0, $MENTION_DESCRIPTOR
            invoke-virtual {p0}, ${postTextModels.mentionStartIdxGetter.smaliReference()}
            move-result p0
            return p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionEndIdx",
        OBJECT_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
        """
            check-cast p0, $MENTION_DESCRIPTOR
            invoke-virtual {p0}, ${postTextModels.mentionEndIdxGetter.smaliReference()}
            move-result p0
            return p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionScreenName",
        OBJECT_DESCRIPTOR,
        STRING_DESCRIPTOR,
        """
            check-cast p0, $MENTION_DESCRIPTOR
            invoke-virtual {p0}, ${postTextModels.mentionScreenNameGetter.smaliReference()}
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getPostAuthorScreenName",
        OBJECT_DESCRIPTOR,
        STRING_DESCRIPTOR,
        """
            check-cast p0, $postDescriptor
            invoke-virtual {p0}, ${postTextModels.postAuthorGetter.smaliReference()}
            move-result-object p0
            if-eqz p0, :piko_newx_post_author_null
            invoke-interface {p0}, ${postTextModels.userScreenNameGetter.smaliReference()}
            move-result-object p0
            return-object p0
            :piko_newx_post_author_null
            const/4 p0, 0x0
            return-object p0
        """.trimIndent(),
    )
}
