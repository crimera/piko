package crimera.patches.twitter.misc.bringbacktwitter.custromstringsupdater

import app.revanced.patcher.data.ResourceContext
import java.io.FileWriter

object pt_rBR {
    val values = mapOf(
        "button_action_add_tweet" to "Adicionar um tweet",
        "button_action_options_tweet" to "Opções do tweet",
        "button_action_retweet" to "Retweetar",
        "button_new_tweet" to "Novo tweet",
        "button_status_retweeted" to "Retweetar (retweetado)",
        "chrome_action_post" to "Tweet",
        "ps__share_post_tweet" to "Tweet",
        "rux_landing_page_title" to "Tweet",
        "tweet_fab_item" to "Tweet",
        "tweet_title" to "Tweet",
        "search_twitter" to "Buscar Twitter",
        "search_hint" to "Buscar Twitter",
        "ps__accessibility_retweet_broadcast_button" to "Retweetar",
        "shortcut_retweet_tweet" to "Retweetar",
        "social_you_retweeted" to "Você retweetou",
        "composer_hint_self_thread" to "Adicionar outro tweet",
        "confirm_delete_shared_tweet_description" to "Tem certeza de que deseja remover este tweet?",
        "confirm_delete_shared_tweet_title" to "Remover tweet",
        "conversation_control_reply_restricted_dialog_title" to "Tweet não enviado",
        "conversation_control_reply_restricted_error" to "Tweet não enviado. As respostas estão restritas.",
        "conversations_alternative_reply_hint" to "Tweet sua resposta",
        "conversations_other_tweets" to "Outros tweets",
        "curation_i_dont_like_this_tweet" to "Eu não gosto desse tweet",
        "date_posted" to "Eu não gosto desse tweet",
        "post_tweet" to "Tweet",
        "deleted_tweet_title" to "Este tweet foi excluído.",
        "dm_sensitive_tweet_interstitial_header" to "Este tweet pode conter material sensível",
        "dm_untrusted_tweet_interstitial_header" to "Este tweet está oculto",
        "empty_profile_tweets_tab_title" to "Você ainda não tweetou",
        "feedback_action_report_tweet" to "Denunciar tweet",
        "feedback_tweet_unavailable" to "Este tweet não está disponível.",
        "filter_item_tweets" to "Tweets",
        "filter_item_tweets_and_replies" to "Tweets & respostas",
        "profile_tab_title_timeline" to "Tweets",
        "users_turn_off_retweets" to "Desativar retweets",
        "tweets_retweet" to "Retweetar",
        "tweets_retweeted" to "%s retweetou",
        "tweets_undo_retweet_vertical" to "Desfazer retweet",
        "users_turn_on_retweets" to "Ativar retweets",
        "ps__post_broadcast_twitter" to "Tweetar no Twitter",
        "ps__retweet_broadcast_action" to "Retweetar no Twitter",
        "retweeters_title" to "Retweetado por",
        "icon_view_tweet_activity" to "Ver atividade do tweet",
        "a11y_views_text" to "Ver atividade do tweet",
        "tweet_analytics_title" to "Atividade do tweet",
        "tweets_delete_title" to "Excluir tweet",
        "share_tweet_sheet_title" to "Compartilhar tweet",
        "view_quote_tweet" to "Ver tweet",
        "view_tweet" to "Ver tweet",
        "view_tweet_text" to "Ver tweet",
        "quote_label_subtitle" to "Adicione um comentário, foto ou GIF antes de compartilhar este tweet",
        "retweet_label_subtitle" to "Compartilhe este tweet com seus seguidores",
        "tweet_added_to_your_bookmarks" to "Tweet adicionado aos seus Itens salvos",
        "tweet_author" to "Autor do tweet",
        "tweets_retweeted_accessibility_description" to "Tweetado por %s",
        "tweets_topic_accessibility_description" to "Tweet do tópico %s",
        "tweets_unauthorized_error" to "Desculpe, você não está autorizado a ver esses tweets.",
        "ps__posted_on_twitter" to "*%s* tweetou em",
        "ps__retweeted_on_twitter" to "*%s* retweetou em",
        "tweet_removed_from_your_bookmarks" to "Tweet removido dos seus Itens salvos"
    )

    /*
     * The official X app doesn't have Brazilian Portuguese string resources.
     * So, add the strings instead of trying to replace them.
     */
    fun updateStrings(context: ResourceContext) {
        if (!context["res/values-pt"].exists()) {
            // User might excluded the Portuguese split intentionally when they merged split APKs.
            // In that case, there is no need to add the pt-rBR strings.
            return
        }

        val stringsFile = context["res/values-pt-rBR/strings.xml"]

        // Note: If SettingsPatch was executed prior to this patch, stringsFile will exist.
        if (!stringsFile.exists()) {
            context["res/values-pt-rBR"].mkdirs()
            FileWriter(stringsFile).use {
                it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
            }
        }

        // Append strings from the map.
        context.xmlEditor[stringsFile.toString()].use {
            val document = it.file
            val parentNode = document.getElementsByTagName("resources").item(0)

            for ((key, value) in values) {
                val newElement = document.createElement("string")
                newElement.setAttribute("name", key)
                newElement.textContent = value

                parentNode.appendChild(newElement)
            }
        }
    }
}
