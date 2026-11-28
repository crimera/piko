/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.Opcode

private const val CACHE = "$DOWNLOAD_DESCRIPTOR/InstagramImageCache;"
private const val IMAGE_URL = "Lcom/instagram/common/typedurl/ImageUrl;"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"
private const val MAP = "Ljava/util/Map;"
private const val INPUT = "Ljava/io/InputStream;"
private const val JSON = "Lorg/json/JSONObject;"

internal val instagramImageCachePatch = patch {
    dependsOn(settingsPatch)

    execute {
        val disk = Fingerprint(
            name = "run",
            returnType = "V",
            filters = listOf(
                string("DiskCacheImageLoader.diskCacheLookup"),
                methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf(IMAGE_URL), returnType = "Z"),
                methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf(STRING, MAP)),
                fieldAccess(opcode = Opcode.IGET_OBJECT, type = OBJECT),
                string("scan"),
                fieldAccess(opcode = Opcode.IGET_OBJECT, type = JSON),
                string("cache_key_type"),
                methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), returnType = STRING),
                string("DiskCacheImageLoader.readBytesFromDiskCacheEntry"),
            ),
        ).match()
        val (_, image, read, optional) = disk.instructionMatches
        val json = disk.instructionMatches[5]
        val readBytes = disk.instructionMatches.last()
        val instructions = disk.method.implementation!!.instructions.toList()
        val imageRegister = image.instruction.registersUsed.single()
        val (cacheRegister, keyRegister, _) = read.instruction.registersUsed
        // The host keeps the ImageUrl live through the lookup and uses it to check content_id.
        if (instructions.subList(image.index + 1, read.index)
                .any { it.registersUsed.firstOrNull() == imageRegister }) {
            throw PatchException("Instagram image URL register changed before the cache lookup")
        }
        val metadata = instructions.subList(optional.index + 1, json.index)
            .mapNotNull { it.getReference<FieldReference>() }
            .distinctBy { it.toString() }
            .single { it.type == optional.fieldReference.definingClass }
        val entryFields = classDefBy(metadata.definingClass).fields
        val stream = entryFields.single { it.type != metadata.type &&
            !AccessFlags.STATIC.isSet(it.accessFlags) }
        val beforeRead = instructions[readBytes.index - 1].getReference<FieldReference>()
        if (beforeRead.toString() != stream.toString()) {
            throw PatchException("Instagram image cache entry does not supply the expected input stream")
        }
        disk.method.addInstructions(
            read.index,
            "invoke-static {v$imageRegister, v$cacheRegister, v$keyRegister}, $CACHE->observe($OBJECT$OBJECT$STRING)V",
        )

        val contentId = disk.instructionMatches[7].methodReference
        for ((name, getter) in mapOf("address" to "$IMAGE_URL->getUrl()$STRING", "contentId" to contentId.toString())) {
            Fingerprint(definingClass = CACHE, name = name).method.addInstructions(
                0,
                """
                check-cast p0, $IMAGE_URL
                invoke-interface {p0}, $getter
                move-result-object p0
                return-object p0
                """.trimIndent(),
            )
        }
        val lookup = read.methodReference
        val value = optional.fieldReference
        Fingerprint(definingClass = CACHE, name = "readEntry").method.addInstructions(
            0,
            """
            check-cast p0, ${lookup.definingClass}
            invoke-interface {p0, p1, p2}, $lookup
            move-result-object p0
            iget-object p0, p0, $value
            if-eqz p0, :miss
            check-cast p0, ${metadata.definingClass}
            iget-object p1, p0, $stream
            iget-object p0, p0, $metadata
            iget-object p0, p0, $value
            if-eqz p0, :validate
            check-cast p0, ${json.fieldReference.definingClass}
            iget-object p0, p0, ${json.fieldReference}
            :validate
            check-cast p0, $JSON
            invoke-static {p1, p0, p3}, $CACHE->complete($INPUT$JSON$STRING)$INPUT
            move-result-object p0
            return-object p0
            :miss
            const/4 p0, 0x0
            return-object p0
            """.trimIndent(),
        )
        Fingerprint(definingClass = USER_SESSION_CLASS, name = "endSessionAndBroadcast").method
            .addInstructions(0, "invoke-static {}, $CACHE->clear()V\ninvoke-static {}, $DOWNLOAD_DESCRIPTOR/InstagramVideoCache;->clear()V")
    }
}
