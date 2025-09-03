package crimera.patches.twitter.models

import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.fingerprint.MethodFingerprintResult
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.Reference

fun Reference.extractDescriptors(): List<String> {
    val regex = Regex("L[^;]+;")
    return regex.findAll(this.toString()).map { it.value }.toList()
}

abstract class EntityMethodFingerprint(
    private val className: String,
    private val methodName: String,
) : MethodFingerprint(customFingerprint = { methodDef, classDef ->
        methodDef.name == methodName && classDef.toString() == "Lapp/revanced/integrations/twitter/model/$className;"
    })

fun EntityMethodFingerprint.changeStringAt(
    index: Int,
    value: String,
) {
    val method = this.result?.mutableMethod
    method?.getInstructions()?.filter { it.opcode == Opcode.CONST_STRING }?.get(index)?.let { instruction ->
        val register = (instruction as BuilderInstruction21c).registerA
        method.replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
    } ?: throw PatchException("const-string not found for method: ${method?.name} at $index")
}

fun EntityMethodFingerprint.changeFirstString(value: String) {
    this.changeStringAt(0, value)
}

fun MethodFingerprintResult.getReference(index: Int): Reference = (this.mutableMethod.getInstruction<ReferenceInstruction>(index).reference)

fun MethodFingerprintResult.getMethodName(index: Int): String = (this.getReference(index) as DexBackedMethodReference).name

fun MethodFingerprintResult.getFieldName(index: Int): String = (this.getReference(index) as FieldReference).name

// --------------- Tweet
internal object TweetObjectFingerprint : MethodFingerprint(strings = listOf("https://x.com/%1\$s/status/%2\$d"))

internal object TweetUsernameFingerprint : EntityMethodFingerprint("Tweet", "getTweetUsername")

internal object TweetProfileNameFingerprint : EntityMethodFingerprint("Tweet", "getTweetProfileName")

internal object TweetUserIdFingerprint : EntityMethodFingerprint("Tweet", "getTweetUserId")

internal object TweetMediaFingerprint : EntityMethodFingerprint("Tweet", "getMedias")

internal object TweetInfoFingerprint : EntityMethodFingerprint("Tweet", "getTweetInfo")

internal object TweetLongTextFingerprint : EntityMethodFingerprint("Tweet", "getLongText")

internal object TweetShortTextFingerprint : EntityMethodFingerprint("Tweet", "getShortText")

internal object GetUserNameMethodCaller : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "Ref_ID (Tweet ID)",
            "Name",
            "User Name",
        ),
)

internal object TweetMediaEntityClassFingerprint : MethodFingerprint(strings = listOf("EntityList{mEntities="))

// --------------- Extended Media Entity

internal object ExtMediaHighResVideoMethodFinder : MethodFingerprint(
    strings =
        listOf(
            "long_press_menu",
            "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment",
        ),
)

internal object ExtMediaHighResVideoFingerprint : EntityMethodFingerprint("ExtMediaEntities", "getHighResVideo")

internal object ExtMediaGetImageMethodFinder : MethodFingerprint(
    strings =
        listOf(
            "type",
            "id",
        ),
    customFingerprint = { _, classDef ->
        classDef.toString().contains("Lcom/twitter/model/json/unifiedcard/JsonAppStoreData;")
    },
)

internal object ExtMediaGetImageFingerprint : EntityMethodFingerprint("ExtMediaEntities", "getImageUrl")

// --------------- TweetInfo
internal object TweetInfoObjectFingerprint : MethodFingerprint(
    strings =
        listOf(
            "flags",
            "lang",
            "supplemental_language",
        ),
    customFingerprint = { methodDef, classDef ->
        methodDef.parameters.size == 2 && classDef.contains("/tdbh/")
    },
)

internal object TweetLangFingerprint : EntityMethodFingerprint("TweetInfo", "getLang")

internal object LongTweetObjectFingerprint : MethodFingerprint(strings = listOf("NoteTweet(id=", ", text="))
