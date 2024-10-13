package crimera.patches.twitter.misc.bringbacktwitter.custromstringsupdater

import app.revanced.patcher.data.ResourceContext

object ja {
    /*
     * Instead of defining strings in the map, use a custom implementation
     * that reads the files and replaces texts directly.
     * Reason: https://t.me/pikopatches/1/17339
     */
    fun updateStrings(context: ResourceContext) {
        context["res/values-ja"].listFiles()?.forEach {
            it.writeText(
                it.readText()
                    .replace("X", "Twitter")
                    .replace("ポスト", "ツイート")
            )
        }
    }
}