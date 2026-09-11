package app.crimera.patches.newx.misc.downloads

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

private const val HELPER = "Lapp/morphe/extension/newx/misc/MultiAppDownloads;"

@Suppress("unused")
val newXMultiAppDownloadsPatch = bytecodePatch(
    name = "NewX: Repair clone profile public downloads",
    description = "Saves public media in the active OnePlus/OPPO/Xiaomi clone profile, preserving native callbacks and normal-user DownloadManager.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_NEW_X)
    dependsOn(newXExtensionPatch)
    execute {
        val matches = Fingerprint(
            definingClass = "Lcom/x/network/",
            returnType = "V",
            parameters = listOf("Landroid/net/Uri;", "Ljava/lang/String;", "Ljava/lang/String;",
                "Ljava/util/Map;", "Ljava/lang/String;", "L", "Z"),
            filters = listOf(methodCall(definingClass = "Landroid/app/DownloadManager;", name = "enqueue")),
        ).scopedMatchAll()
        val match = requireExactlyOne("native public-download dispatcher", matches) { it.method.toString() }
        val owner = match.classDef
        val contextField = requireExactlyOne("native downloader context in ${owner.type}",
            owner.fields.filter { it.type == "Landroid/content/Context;" })
        if (!AccessFlags.PUBLIC.isSet(contextField.accessFlags) || AccessFlags.STATIC.isSet(contextField.accessFlags)
            || !AccessFlags.PUBLIC.isSet(owner.accessFlags))
            throw PatchException("Native context must be an accessible instance field: $contextField")
        val callbackType = match.method.parameterTypes[5].toString()
        val callbackClass = mutableClassDefBy(callbackType)
        if (!AccessFlags.INTERFACE.isSet(callbackClass.accessFlags) || !AccessFlags.PUBLIC.isSet(callbackClass.accessFlags))
            throw PatchException("Native download callback must be a public interface: $callbackType")
        val callback = requireExactlyOne("native download callback in $callbackType", callbackClass.methods.filter {
            it.returnType == "V" && !AccessFlags.STATIC.isSet(it.accessFlags) &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;", "Ljava/lang/String;", "Z")
        })
        if (!AccessFlags.PUBLIC.isSet(callback.accessFlags))
            throw PatchException("Native download callback must be public: $callback")
        val helper = mutableClassDefBy(HELPER)
        requireExactlyOne("clone native context bridge", helper.methods.filter { it.name == "nativeContext" }).addInstructions(0, """
            check-cast p0, ${owner.type}
            iget-object p0, p0, $contextField
            return-object p0
        """.trimIndent())
        requireExactlyOne("clone native callback bridge", helper.methods.filter { it.name == "nativeCallback" }).addInstructions(0, """
            check-cast p0, $callbackType
            invoke-interface {p0, p2, p3, p1}, $callback
            return-void
        """.trimIndent())
        val method = match.method
        if (method.implementation!!.registerCount <= 8 || AccessFlags.STATIC.isSet(method.accessFlags))
            throw PatchException("Native download dispatcher register layout changed: $method")
        method.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p7}, $HELPER->tryNativeDownload(Ljava/lang/Object;Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/Object;Z)Z
            move-result v0
            if-eqz v0, :original_download
            return-void
        """.trimIndent(), ExternalLabel("original_download", method.instructions.first()))
        // Keep inline routing unchanged when users deselect this optional patch.
        requireExactlyOne("clone compatibility activation", helper.methods.filter { it.name == "isPatchApplied" })
            .addInstructions(0, """
                const/4 v0, 0x1
                return v0
            """.trimIndent())
        println("Clone download bridge: $method; context=$contextField; callback=$callback")
    }
}
