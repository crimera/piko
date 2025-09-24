package app.revanced.extension.twitter.patches.nativeFeatures.readerMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import app.revanced.extension.twitter.Utils;
import app.revanced.extension.shared.StringRef;

import org.json.JSONObject;
import org.json.JSONArray;

public class ReaderModeTemplate {

  private static final String GROK_ANALYSE_THREAD = StringRef.str("piko_native_reader_mode_grok_thread");
  private static final String GROK_ANALYSE_AUTHOR = StringRef.str("piko_native_reader_mode_grok_author");
  private static final String TITLE_SOURCE = StringRef.str("piko_native_reader_mode_source");
  private static final String TITLE_PUBLISHED = StringRef.str("piko_native_reader_mode_published");
  private static final String TITLE_TOT_POSTS = StringRef.str("piko_native_reader_mode_total_post");
  private static final String SHARE_POST = StringRef.str("piko_native_reader_mode_copy_post");

  private static String getGrokIcon(String slug, String text) {
    return "<a href=\"https://x.com/i/grok?text=" + slug
        + "\" target=\"_blank\" rel=\"noopener\" class=\"grok-button\">\n" +
        "<span class=\"grok-icon\">\n" +
        "<svg width=\"18\" height=\"18\" viewBox=\"0 0 33 32\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\" aria-label=\"Grok\">\n"
        +
        "<path fill=\"#fff\" d=\"M12.745 20.54l10.97-8.19c.539-.4 1.307-.244 1.564.38 1.349 3.288.746 7.241-1.938 9.955-2.683 2.714-6.417 3.31-9.83 1.954l-3.728 1.745c5.347 3.697 11.84 2.782 15.898-1.324 3.219-3.255 4.216-7.692 3.284-11.693l.008.009c-1.351-5.878.332-8.227 3.782-13.031L33 0l-4.54 4.59v-.014L12.743 20.544zm-2.263 1.987c-3.837-3.707-3.175-9.446.1-12.755 2.42-2.449 6.388-3.448 9.852-1.979l3.72-1.737c-.67-.49-1.53-1.017-2.515-1.387-4.455-1.854-9.789-.931-13.41 2.728-3.483 3.523-4.579 8.94-2.697 13.561 1.405 3.454-.899 5.898-3.22 8.364C1.49 30.2.666 31.074 0 32l10.478-9.466\"/>\n"
        +
        "</svg>\n" +
        "</span>\n" +
        "<span class=\"grok-text\">" + text + "</span>\n" +
        "</a>\n";
  }

  private static String getColorScheme() {
    return ":root{\n" +
        "    --bg-body: #ffffff;\n" +
        "    --bg-container: #fefefe;\n" +
        "    --text-primary: #262626;\n" +
        "    --text-secondary: #606770;\n" +
        "    --text-accent: #1a73e8;\n" +
        "    --border-color: #dcdcdc;\n" +
        "    --media-shadow: rgba(26, 115, 232, 0.3);\n" +
        "    --highlight-bg: #e1ecff;\n" +
        "    --highlight-color: #1a56db;\n" +
        "    --quoted-bg: #f5f8ff;\n" +
        "    --quoted-border: #a3b4d9;\n" +
        "    --quoted-text: #38456d;\n" +
        "    --footer-border: #e1e4e8;\n" +
        "    --footer-text: #70757a;\n" +
        "    --link-color: #1a73e8;\n" +
        "    --link-hover: underline;\n" +
        "    --author-border: #d9d9d9;\n" +
        "    --author-desc: #5e6366;\n" +
        "    --author-handle: #65676b;\n" +
        "    }\n" +
        "body.dark {\n" +
        "     --bg-body: #121212;\n" +
        "     --bg-container: #1e1e1e;\n" +
        "     --text-primary: #e0e0e0;\n" +
        "     --text-secondary: #8a9ba8;\n" +
        "     --text-accent: #80c8ff;\n" +
        "     --border-color: #333;\n" +
        "     --media-shadow: rgba(65, 105, 225, 0.8);\n" +
        "     --highlight-bg: #335577;\n" +
        "     --highlight-color: #c1d9ff;\n" +
        "     --quoted-bg: #2d2e2f;\n" +
        "     --quoted-border: #5a79a0;\n" +
        "     --quoted-text: #aac6ff;\n" +
        "     --footer-border: #333;\n" +
        "     --footer-text: #6a7b8d;\n" +
        "     --link-color: #7ab8ff;\n" +
        "     --link-hover: underline;\n" +
        "     --author-border: #2f3943;\n" +
        "     --author-desc: #89a1b0;\n" +
        "}\n" +
        "body.dim {\n" +
        "--bg-body: #16202a	;\n" +
        "--bg-container: #192734;\n" +
        "--text-primary: #a3b1c2;\n" +
        "--text-secondary: #7c8a9e;\n" +
        "--text-accent: #6ea7ff;\n" +
        "--border-color: #32475b;\n" +
        "--media-shadow: rgba(77, 117, 179, 0.67);\n" +
        "--highlight-bg: #24436e;\n" +
        "--highlight-color: #afd1ff;\n" +
        "--quoted-bg: #223544;\n" +
        "--quoted-border: #5178ab;\n" +
        "--quoted-text: #9dbadb;\n" +
        "--footer-border: #32475b;\n" +
        "--footer-text: #73859a;\n" +
        "--link-color: #0070ff;\n" +
        "--author-border: #32475b;\n" +
        "--author-desc: #84a2c3;\n" +
        "--author-handle: #7c8a9e;\n" +
        "}\n";
       
  }

  public static String getHTMLHeader() {

    String colorScheme = getColorScheme();

    String rest = "body {\n" +
        "  background: var(--bg-body);\n" +
        "  font-family: 'Georgia', 'Times New Roman', serif;\n" +
        "  color: var(--text-primary);\n" +
        "  margin: 0;\n" +
        "  padding: 0 0 3rem 0;\n" +
        "  transition: background 0.3s ease, color 0.3s ease;\n" +
        "}\n" +

        ".news-container {\n" +
        "  max-width: 680px;\n" +
        "  margin: 3rem auto;\n" +
        "  background: var(--bg-container);\n" +
        "  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.1);\n" +
        "  border-radius: 8px;\n" +
        "  padding: 2.3rem 2.3rem 2rem 2.3rem;\n" +
        "  color: var(--text-primary);\n" +
        "  transition: background 0.3s ease, color 0.3s ease;\n" +
        "}\n" +

        ".news-header {\n" +
        "  border-bottom: 3px solid var(--border-color);\n" +
        "  margin-bottom: 1.3rem;\n" +
        "  padding-bottom: 1.4rem;\n" +
        "  display: flex;\n" +
        "  flex-direction: column;\n" +
        "  gap: 0.4em;\n" +
        "}\n" +

        ".news-meta {\n" +
        "  font-size: 1em;\n" +
        "  color: var(--text-secondary);\n" +
        "  transition: color 0.3s ease;\n" +
        "}\n" +

        ".author-block {\n" +
        "display: flex;\n" +
        "align-items: center;\n" +
        "gap: 1rem;\n" +
        "padding-bottom: 1rem;\n" +
        "max-width: 400px;\n" +
        "font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
        "}\n" +

        ".author-block img {\n" +
        "width: 56px;\n" +
        "height: 56px;\n" +
        "border-radius: 50%;\n" +
        "border: 2px solid #0078d4;\n" +
        "object-fit: cover;\n" +
        "}\n" +

        ".author-details {\n" +
        "display: flex;\n" +
        "flex-direction: column;\n" +
        "}\n" +

        ".author-details .name {\n" +
        "font-weight: 700;\n" +
        "font-size: 1.1rem;\n" +
        "color: var(--highlight-color);\n" +
        "}\n" +

        ".author-details a {\n" +
        "font-size: 0.9rem;\n" +
        "color: var(--text-secondary);\n" +
        "text-decoration: none;\n" +
        "}\n" +

        ".article-body {\n" +
        "  font-size: 1.14rem;\n" +
        "  line-height: 1.7;\n" +
        "  margin-bottom: 2rem;\n" +
        "  color: var(--text-primary);\n" +
        "  transition: color 0.3s ease;\n" +
        "}\n" +

        ".media {\n" +
        "  margin: 1.1em 0;\n" +
        "  text-align: center;\n" +
        "}\n" +

        ".media img,\n" +
        ".media video {\n" +
        "  max-width: 100%;\n" +
        "  border-radius: 10px;\n" +
        "  box-shadow: 0 0 12px var(--media-shadow);\n" +
        "  transition: box-shadow 0.3s ease;\n" +
        "}\n" +

        ".highlight {\n" +
        "  font-weight: bold;\n" +
        "  background: var(--highlight-bg);\n" +
        "  padding: 0.08em 0.25em;\n" +
        "  border-radius: 4px;\n" +
        "  color: var(--highlight-color);\n" +
        "  transition: background 0.3s ease, color 0.3s ease;\n" +
        "}\n" +

        ".quoted-section {\n" +
        "  background: var(--quoted-bg);\n" +
        "  border-left: 4px solid var(--quoted-border);\n" +
        "  padding: 1em 1em 1em 1.4em;\n" +
        "  margin: 1.9em 0 0.9em 0;\n" +
        "  border-radius: 7px;\n" +
        "  font-size: 1.02em;\n" +
        "  color: var(--quoted-text);\n" +
        "  transition: background 0.3s ease, border-color 0.3s ease, color 0.3s ease;\n" +
        "}\n" +

        ".quoted-author {\n" +
        "  font-weight: 600;\n" +
        "  color: var(--link-color);\n" +
        "  font-size: 0.97em;\n" +
        "  margin-bottom: 0.14em;\n" +
        "  display: block;\n" +
        "  transition: color 0.3s ease;\n" +
        "}\n" +

        ".article-footer {\n" +
        "  margin-top: 2.6em;\n" +
        "  padding-top: 1.4em;\n" +
        "  border-top: 1.5px solid var(--footer-border);\n" +
        "  font-size: 0.95em;\n" +
        "  color: var(--footer-text);\n" +
        "  transition: border-color 0.3s ease, color 0.3s ease;\n" +
        "}\n" +
        "a {\n" +
        "  color: var(--link-color);\n" +
        "  text-decoration: none;\n" +
        "  word-break: break-all;\n" +
        "  transition: color 0.3s ease;\n" +
        "}\n" +
        ".article-footer a {\n" +
        "  font-size: 1rem;\n" +
        "}\n" +

        "@media (max-width: 700px) {\n" +
        "  .news-container {\n" +
        "    padding: 1.2rem 0.8rem;\n" +
        "  }\n" +
        "}\n" +
        ".grok-button {\n" +
        "    display: inline-flex;\n" +
        "    align-items: center;\n" +
        "    gap: 0.5em;\n" +
        "    padding: 0.3em 1.2em;\n" +
        "    background-color:#000;\n" +
        "    border-radius: 9999px; /* oval shape */\n" +
        "    color: white;\n" +
        "    font-weight: 700;\n" +
        "    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
        "    font-size: 0.7rem;\n" +
        "    text-decoration: none;\n" +
        "    cursor: pointer;\n" +
        "  }\n" +
        "  .grok-icon svg {\n" +
        "    display: block;\n" +
        "  }" +
        ".author-details .grok-button {\n" +
        "font-size: 0.7rem;\n" +
        "color: #fff;\n" +
        "}\n" +
        ".article-footer .grok-button{\n" +
        "font-size: 0.7rem;\n" +
        "color: #fff;\n" +
        "}"+
         " .toggle-container {\n" + 
        "    position: absolute;\n" + 
        "    top: 0.5rem;\n" + 
        "    right: 1rem;\n" + 
        "    background: var(--bg-container);\n" + 
        "    border-radius: 20px;\n" + 
        "    box-shadow: 0 2px 8px rgba(0,0,0,0.5);\n" + 
        "    padding: 0.4rem 0.8rem;\n" + 
        "    font-family: sans-serif;\n" + 
        "    color: var(--text-primary);\n" + 
        "    cursor: pointer;\n" + 
        "    user-select: none;\n" + 
        "    transition: background 0.3s ease, color 0.3s ease;\n" + 
        "    z-index: 1000;\n" + 
    "    }";

    return "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" /><style>\n\n" + colorScheme
        + "\n\n" + rest + "\n\n</style></head>";
  }

  private static String getHTMLTitle(JSONObject thread) throws Exception {
    String html = "";
    String template = " <div class=\"author-block\">\n" +
        "     <img src=\"{profilepic}\"alt=\"{name}\"/>\n" +
        "     <div class=\"author-details\">\n" +
        "         <span class=\"name\">{name}</span>\n" +
        "         <a href=\"https://x.com/{username}\" target=\"_blank\">@{username}</a>\n" +
        "    {grokUser}\n" +
        " </div></div>\n" +
        " <div class=\"news-header\">\n" +
        "     <div class=\"news-meta\">\n" +
        TITLE_PUBLISHED + ": {formattedDate} · " + TITLE_SOURCE
        + ": <a href=\"{postLink}\" target=\"_blank\">X Thread</a>\n"
        +
        "     </div>\n" +
        "     <div class=\"news-meta\">\n" +
        TITLE_TOT_POSTS + ": {totalTweets}\n" +
        "     </div>\n" +
        "     <div class=\"news-meta\">\n" +
        "{grokPost}\n" +
        "     </div>\n" +
        " </div>\n";

    String tweetId = thread.getString("id");
    JSONObject author = thread.getJSONObject("author");
    String name = author.getString("name");
    String username = author.getString("username");
    String profilepic = author.getString("profileImageUrl");
    String postLink = "https://x.com/" + username + "/status/" + tweetId;

    long createdAt = thread.getLong("createdAt");
    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault());
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd, yyyy");
    String formattedDate = dateTime.format(formatter);

    int totalTweets = thread.getInt("totalTweets");
    html = template
        .replace("{formattedDate}", formattedDate)
        .replace("{username}", username)
        .replace("{name}", name)
        .replace("{profilepic}", profilepic)
        .replace("{tweetId}", tweetId)
        .replace("{postLink}", postLink)
        .replace("{grokUser}", getGrokIcon("@" + username, GROK_ANALYSE_AUTHOR))
        .replace("{grokPost}", getGrokIcon(postLink, GROK_ANALYSE_THREAD))
        .replace("{totalTweets}", String.valueOf(totalTweets));
    return html;

  }

  private static String[] getHTMLMedia(int type, JSONObject media) throws Exception {
    String html = "<div class=\"media\">{mediaTag}</div><br/>";
    String mediaTag = "";
    // required to remove pic url.
    String tco = media.getString("url");

    // 0= img, 1 = vid
    if (type == 0) {
      var src = media.getString("src");
      mediaTag = "<img src=\"" + src + "\" alt=\"image.jpg\" /><br/>";
    } else {
      var thumbnail = media.getString("posterUrl");
      var videoUrl = media.getJSONArray("sources").getJSONObject(0).getString("url");
      mediaTag = "   <video controls poster=\"{thumbnail}\"; width=\"320\">\n" +
          "     <source src=\"{videoUrl}\" type=\"video/mp4\" />\n" +
          " </video><br/>\n";
      mediaTag = mediaTag.replace("{thumbnail}", thumbnail)
          .replace("{videoUrl}", videoUrl);

    }
    return new String[] { tco, html.replace("{mediaTag}", mediaTag) };
  }

  private static String sanitizeText(JSONObject tweet) throws Exception {
    String text = tweet.getString("text");
    if (!tweet.isNull("urls")) {
      JSONArray urls = tweet.getJSONArray("urls");
      for (int i = 0; i < urls.length(); i++) {
        JSONObject url = urls.getJSONObject(i);
        String tco = url.getString("url");
        String expUrl = url.getString("expandedUrl");
        text = text.replace(tco, expUrl);
      }

    }
    return text;
  }

  private static String getHTMLTweet(JSONObject tweet, boolean quoted)
      throws Exception {
    String html = "{content}";
    String content = "";

    if (quoted) {
      String tweetId = tweet.getString("id");
      JSONObject author = tweet.getJSONObject("author");
      String name = author.getString("name");
      String username = author.getString("username");
      html = "<div class=\"quoted-section\">\n" +
          "     <span class=\"quoted-author\">{name} (@{username})</span><br/>\n" +
          // "{text}\n" +
          "     {content}\n" +
          "<!--      <a href=\"https://x.com/{username}/status/{tweetId}\" target=\"_blank\">Full quoted post</a>-->\n"
          +
          "</div><br/>";
      html = html.replace("{username}", username)
          .replace("{name}", name)
          .replace("{tweetId}", tweetId);
    }

    if (!tweet.isNull("text")) {

      content = sanitizeText(tweet) + "<br/>";
    }

    if (!tweet.isNull("photos")) {
      JSONArray images = tweet.getJSONArray("photos");
      for (int i = 0; i < images.length(); i++) {
        JSONObject item = images.getJSONObject(i);
        String[] cnt = getHTMLMedia(0, item);
        String tco = cnt[0];
        content = content.replace(tco, "") + cnt[1];
      }
    }

    if (!tweet.isNull("media")) {
      JSONArray videos = tweet.getJSONArray("media");
      for (int i = 0; i < videos.length(); i++) {
        JSONObject item = videos.getJSONObject(i);
        String[] cnt = getHTMLMedia(1, item);
        String tco = cnt[0];
        content = content.replace(tco, "") + cnt[1];
      }
    }

    if (!tweet.isNull("quoted")) {
      JSONObject quotedTweet = tweet.getJSONObject("quoted");
      content += getHTMLTweet(quotedTweet, true);
    }

    return html.replace("{content}", content);
  }

  private static String getHTMLFooter(JSONObject thread) throws Exception {
    String tweetId = thread.getString("id");
    String postLink = "https://x.com/dummy/status/" + tweetId;

    String template = "<div class=\"article-footer\">\n" +
        getGrokIcon(postLink, GROK_ANALYSE_THREAD) + "\n" +
        "    <a href=\"https://twitter-thread.com/t/{tweetId}\" target=\"_blank\">twitter-thread.com</a>\n" +
        "</div>\n";

    return template
        .replace("{tweetId}", tweetId);
  }

  public static String generateHtml(JSONObject threads) throws Exception {

    String content = "";
    JSONObject thread = threads.getJSONObject("thread");
    String tweetId = thread.getString("id");
    String title = getHTMLTitle(thread);
    JSONArray tweets = thread.getJSONArray("tweets");
    for (int i = 0; i < tweets.length(); i++) {
      JSONObject tweet = tweets.getJSONObject(i);
      content += getHTMLTweet(tweet, false);
    }
    String footer = getHTMLFooter(thread);
    return generateHtml(tweetId,title + "\n" + content + "\n" + footer);
  }

  public static String generateHtml(String tweetId,String content) {
    String script =  "<script type=\"text/javascript\">" +
          "function copyText() {" +
          "   var textToCopy = 'https://x.com/i/status/"+tweetId+"';" +
          "   Android.copyText(textToCopy);" +  // Call Android interface method
          "}" +
          "</script>"+
          "   </body>\n" +
        " </html>\n";
    String btn = "   <body class=\"{themeClassName}\">\n" +
        "<div class=\"toggle-container\"  onclick=\"copyText()\">"+SHARE_POST+"</div>";
    String html = generateHtml(content);

    html = html.replace("   </body>\n" +
        " </html>\n",script).replace("   <body class=\"{themeClassName}\">\n",btn);
      return html;
  }

  public static String generateHtml(String content) {
    String html = " <html>\n" +
        "   {head}\n" +
        "   <body class=\"{themeClassName}\">\n" +
        "<div class=\"news-container\">\n" +
        "{content}\n" +
        "</div>\n" +
        "   </body>\n" +
        " </html>\n";
    String head = getHTMLHeader();
    return html
        .replace("{head}", head)
        .replace("{content}", content);
  }

}