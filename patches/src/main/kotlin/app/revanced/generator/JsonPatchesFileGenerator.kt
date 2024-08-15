package app.revanced.generator

import app.revanced.patcher.patch.Package
import app.revanced.patcher.patch.Patch
import com.google.gson.GsonBuilder
import java.io.File

internal class JsonPatchesFileGenerator : PatchesFileGenerator {
    override fun generate(patches: Set<Patch<*>>) = patches.map {
        JsonPatch(
            it.name!!,
            it.description,
            it.compatiblePackages,
            it.use,
            it.options.values.map { option ->
                JsonPatch.Option(
                    option.key,
                    option.default,
                    option.values,
                    option.title,
                    option.description,
                    option.required,
                )
            },
        )
    }.let {
        File("patches.json").writeText(GsonBuilder().serializeNulls().create().toJson(it))
    }

    @Suppress("unused")
    private class JsonPatch(
        val name: String? = null,
        val description: String? = null,
        val compatiblePackages: Set<Package>? = null,
        val use: Boolean = true,
        val options: List<Option>,
    ) {
        class Option(
            val key: String,
            val default: Any?,
            val values: Map<String, Any?>?,
            val title: String?,
            val description: String?,
            val required: Boolean,
        )
    }
}
