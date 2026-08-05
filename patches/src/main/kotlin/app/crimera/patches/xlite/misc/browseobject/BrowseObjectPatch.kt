package app.crimera.patches.xlite.misc.browseobject

import app.crimera.patches.xlite.misc.postoptions.BROWSE_OBJECT_ACTION
import app.crimera.patches.xlite.misc.postoptions.xLitePostOption
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.patch.bytecodePatch

private const val OBJECT_BROWSER_HANDLER = "Lapp/morphe/extension/xlite/misc/XLiteObjectBrowserHandler;"

@Suppress("unused")
val xLiteBrowseObjectPatch =
    bytecodePatch(
        name = "X-Lite: Browse tweet object",
        description = "Adds a debug option to browse the tweet object in X-Lite post menus.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteToggle(
            id = "xlite.content.browse_tweet_object",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_xlite_browse_tweet_object"),
            order = 260,
            defaultValue = true,
        )
        xLitePostOption(
            handlerDescriptor = OBJECT_BROWSER_HANDLER,
            actionName = BROWSE_OBJECT_ACTION,
            iconResourceName = "ic_vector_flask_stroke",
            order = 260,
        )
    }
