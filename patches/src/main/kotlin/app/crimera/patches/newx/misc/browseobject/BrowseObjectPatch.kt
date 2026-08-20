package app.crimera.patches.newx.misc.browseobject

import app.crimera.patches.newx.misc.postoptions.BROWSE_OBJECT_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

private const val OBJECT_BROWSER_HANDLER = "Lapp/morphe/extension/newx/misc/NewXObjectBrowserHandler;"

@Suppress("unused")
val newXBrowseObjectPatch =
    bytecodePatch(
        name = "NewX: Browse tweet object",
        description = "Adds a debug option to browse the tweet object in NewX post menus.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSettings {
            category(Categories.ADVANCED) {
                group(Groups.DEBUG_TOOLS) {
                    toggle(
                        id = "newx.content.browse_tweet_object",
                        strings = settingStrings("piko_newx_browse_tweet_object"),
                        order = 100,
                        defaultValue = true,
                    )
                }
            }
        }
        newXPostOption(
            handlerDescriptor = OBJECT_BROWSER_HANDLER,
            actionName = BROWSE_OBJECT_ACTION,
            iconResourceName = "ic_vector_flask_stroke",
            order = 260,
        )
    }
