package your.org.patches.example

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch

@Patch(
    name = "Example Patch",
    description = "This is an example patch to start with.",
    compatiblePackages = [
        CompatiblePackage("com.example.app", ["1.0.0"]),
    ],
)
@Suppress("unused")
object ExamplePatch : BytecodePatch() {
    override fun execute(context: BytecodeContext) {
        // TODO("Not yet implemented")
    }
}
