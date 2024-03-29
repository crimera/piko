package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.*
import org.w3c.dom.Element
import java.io.File

@Patch(
    name = "Bring back twitter",
    description = "Bring back old twitter logo and name",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object BringBackTwitterResourcePatch : ResourcePatch() {
    val mipmapIcons = arrayOf(
        "ic_launcher_twitter",
        "ic_launcher_twitter_round",
        "ic_launcher_twitter_foreground",
    ).map { "$it.webp" }.toTypedArray()

    val drawableIcons = arrayOf(
        "ic_vector_twitter",
        "splash_screen_icon"
    ).map { "$it.xml" }.toTypedArray()

    val sizes = arrayOf(
        "xxxhdpi",
        "xxhdpi",
        "xhdpi",
        "hdpi",
        "mdpi",
    )

    val languages = arrayOf(
        "fa", "de", "sr", "ko", "pt", "ro", "bg",
        "zh-rCN", "tl", "gu", "ar-rEH", "cs", "hr", "hu",
        "bn", "fr", "ja", "uk", "sk", "it", "iw",
        "in", "pl", "hi", "ru", "ms", "th", "nl",
        "en-rGB", "ca", "zh-rTW", "zh-rHK", "el", "ta", "es",
        "kn", "fi", "vi", "ar", "mr", "da", "sv",
        "tr", "nb", ""
    ).map {
        if (it == "") {
            "res/values/strings.xml"
        } else {
            "res/values-$it/strings.xml"
        }
    }

    override fun execute(context: ResourceContext) {

        // Change app name
        languages.forEach { file ->
            context.xmlEditor[file].use {
                val strings = it.file.getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val string = strings.item(i) as Element

                    if (!string.getAttribute("name").contains("api_key")) {
                        string.textContent = string.textContent.replace("X", "Twitter")
                    }
                }
            }
        }

        // app icons
        // drawable icons
        sizes.map { "drawable-$it" }.plus("drawable").map {
            if (it == "drawable") {
                ResourceGroup(it, *drawableIcons)
            } else {
                ResourceGroup(it, "ic_stat_twitter.webp")
            }
        }.forEach {
            context.copyResources("twitter", it)
        }

        // mipmap icons
        sizes.map { "mipmap-$it" }.map {
            if (it == "mipmap-xxhdpi") {
                ResourceGroup(it, *mipmapIcons.plus("fg_launcher_twitter.webp"))
            } else {
                ResourceGroup(it, *mipmapIcons)
            }
        }.forEach {
            context.copyResources("twitter", it)
        }

        // bring back twitter blue
        context.xmlEditor["res/layout/ocf_twitter_logo.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.xmlEditor["res/layout/channels_toolbar_main.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.xmlEditor["res/values/colors.xml"].use {
            it.file.getElementsByTagName("color").asSequence().find { color ->
                (color as Element).getAttribute("name") == "ic_launcher_background"
            }?.textContent = "@color/twitter_blue"
        }
    }

    private fun updateStrings(context: ResourceContext) {
        val stringsFile = context["res/values/strings.xml"]
        val stringsUK = context["res/values-en-rGB/strings.xml"]

        when {
            !stringsUK.isFile -> throw PatchException("$stringsUK file not found.")
            !stringsFile.isFile -> throw PatchException("$stringsFile file not found.")
        }

        // Update strings.xml
        updateStringsFile(stringsFile, context)
        // Update strings-en-rGB.xml (British English)
        updateStringsFile(stringsUK, context)
    }

    private fun updateStringsFile(stringsFile: File, context: ResourceContext) {

        context.xmlEditor[stringsFile.toString()].use { editor ->
            val document = editor.file

            // more strings can be added in this map , irrespective of their order
            val replacementMap = mapOf(
                "button_action_add_tweet" to "Add a tweet",
                "button_action_options_tweet" to "Tweet options",
                "button_action_retweet" to "Retweet",
                "button_new_tweet" to "New tweet",
                "button_status_retweeted" to "Retweet (retweeted)",
                "chrome_action_post" to "Tweet",
                "ps__share_post_tweet" to "Tweet",
                "rux_landing_page_title" to "Tweet",
                "tweet_fab_item" to "Tweet",
                "tweet_title" to "Tweet",
                "search_twitter" to "Search Twitter",
                "search_hint" to "Search Twitter",
                "ps__accessibility_retweet_broadcast_button" to "Retweet",
                "shortcut_retweet_tweet" to "Retweet",
                "social_you_retweeted" to "You retweeted",
                "composer_hint_self_thread" to "Add another tweet",
                "confirm_delete_shared_tweet_description" to "Are you sure you want to remove this tweet?",
                "confirm_delete_shared_tweet_title" to "Remove tweet",
                "conversation_control_reply_restricted_dialog_title" to "Tweet not sent",
                "conversation_control_reply_restricted_error" to "Tweet not sent. Replies are restricted.",
                "conversations_alternative_reply_hint" to "Tweet your reply",
                "conversations_other_tweets" to "Other tweets",
                "curation_i_dont_like_this_tweet" to "I don’t like this tweet",
                "date_posted" to "Date tweeted",
                "post_tweet" to "Tweet",
                "deleted_tweet_title" to "This tweet has been deleted.",
                "dm_sensitive_tweet_interstitial_header" to "This tweet may contain sensitive material",
                "dm_untrusted_tweet_interstitial_header" to "This tweet is hidden",
                "empty_profile_tweets_tab_title" to "You haven’t tweeted yet",
                "feedback_action_report_tweet" to "Report tweet",
                "feedback_tweet_unavailable" to "This tweet is unavailable.",
                "filter_item_tweets" to "Tweets",
                "filter_item_tweets_and_replies" to "Tweets & replies",
                "profile_tab_title_timeline" to "Tweets",
                "users_turn_off_retweets" to "Turn off retweets",
                "tweets_retweet" to "Retweet",
                "tweets_retweeted" to "%s retweeted",
                "tweets_undo_retweet_vertical" to "Undo retweet",
                "users_turn_on_retweets" to "Turn on retweets",
                "ps__post_broadcast_twitter" to "Post on Twitter",
                "ps__retweet_broadcast_action" to "Retweet on Twitter",
                "retweeters_title" to "Retweeted by"
            )

            for ((key, value) in replacementMap) {
                val nodes = document.getElementsByTagName("string")
                var keyReplaced = false

                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    if (node.attributes.getNamedItem("name")?.nodeValue == key) {
                        node.textContent = value
                        keyReplaced = true
                        break
                    }
                }

                // log which keys were not found or failed
                if (!keyReplaced) {
                    println("Key not found: $key")
                }
            }
        }
    }
}
