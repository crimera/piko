/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.utils.changeString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val VIDEO_CACHE = "$DOWNLOAD_DESCRIPTOR/InstagramVideoCache;"
private const val STRING = "Ljava/lang/String;"
private const val URI = "Landroid/net/Uri;"

private object OpenVideoSource : Fingerprint(
    name = "open",
    returnType = "J",
    parameters = listOf("L"),
    filters = listOf(
        string("FbHttpCacheDataSource.open"),
        methodCall(parameters = listOf(URI, STRING, STRING, "Z", "Z", "Z", "Z", "Z"), returnType = STRING),
    ),
)

private object CheckVideoCache : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("CacheManager.isCached"),
        methodCall(parameters = listOf(STRING, "J", "J"), returnType = "Z", opcode = Opcode.INVOKE_VIRTUAL_RANGE),
    ),
)

private object DescribeCacheSpan : Fingerprint(
    name = "toString",
    returnType = STRING,
    filters = listOf(string("CacheSpan{")),
)

internal val instagramVideoCachePatch = patch {
    dependsOn(sharedExtensionPatch)
    execute {
        val cacheCheck = CheckVideoCache
        val managerClass = cacheCheck.originalClassDef
        val cacheClass = classDefBy(cacheCheck.instructionMatches.last().methodReference.definingClass)
        val cache = requireOne("player disk cache field", managerClass.instanceFields.filter { it.type == cacheClass.type })
        val manager = requireOne("player cache manager", OpenVideoSource.originalClassDef.instanceFields.filter { it.type == managerClass.type })
        val spans = requireOne("cached spans lookup", cacheClass.virtualMethods.filter {
            it.parameterTypes == listOf(STRING) && it.returnType == "Ljava/util/NavigableSet;"
        })

        val length = requireOne("total video content length", cacheClass.virtualMethods.filter {
            it.parameterTypes == listOf(STRING) && it.returnType == "J"
        })
        val spanClass = DescribeCacheSpan.originalClassDef
        val file = requireOne("cache span file", spanClass.instanceFields.filter { it.type == "Ljava/io/File;" })
        val spanLongs = DescribeCacheSpan.originalMethod.implementation!!.instructions
            .filter { it.opcode == Opcode.IGET_WIDE }.mapNotNull { it.getReference<FieldReference>() }.toList()
        // CacheSpan.toString prints the last-access time, start position and byte length in order.
        if (spanLongs.size != 3 || spanLongs.distinct().size != 3 ||
            spanLongs.any { it.definingClass != spanClass.type || it.type != "J" }
        ) throw PatchException("Unexpected video cache span description")
        val lookup = Fingerprint(definingClass = VIDEO_CACHE, name = "openTrack")
        lookup.changeString("videoCacheField", cache.name)
        lookup.changeString("videoSpansMethod", spans.name)
        lookup.changeString("videoContentLengthMethod", length.name)
        lookup.changeString("videoSpanFile", file.name)
        lookup.changeString("videoSpanPosition", spanLongs[1].name)
        lookup.changeString("videoSpanLength", spanLongs[2].name)

        val open = OpenVideoSource.method
        val keyCall = OpenVideoSource.instructionMatches.last()
        val args = keyCall.instruction.registersUsed
        val instructions = open.implementation!!.instructions
        val result = instructions[keyCall.index + 1]
        if (args.size != 8 || result.opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException("Unexpected video cache key calculation")
        }
        val url = args.first()
        val key = result.registersUsed.single()
        val index = keyCall.index + 2
        if (key == url) throw PatchException("Video cache key overwrote its URI")
        val inputs = listOf(url, key)
        val high = inputs.filter { it > 15 }
        val scratch = open.reserveFreeRegisters(index, 1 + high.size, 15, open.parameterBlock() + inputs)
        val managerReg = scratch.first()
        val staging = high.zip(scratch.drop(1)).toMap()
        val urlReg = staging[url] ?: url
        val keyReg = staging[key] ?: key
        val moves = staging.entries.joinToString("\n") { (from, to) -> moveObject(to, from) }
        open.addInstructions(index, """
            move-object/from16 v$managerReg, p0
            iget-object v$managerReg, v$managerReg, $manager
            $moves
            invoke-static {v$managerReg, v$urlReg, v$keyReg}, $VIDEO_CACHE->remember(Ljava/lang/Object;$URI$STRING)V
        """.trimIndent())
    }
}
