package crimera.patches.twitter.misc.customize.timelinetabs

import app.revanced.patcher.fingerprint

internal val replySortingInvokeClassFinderFingerprint = fingerprint {
    custom { it,_->
        it.definingClass == "Lcom/twitter/tweetview/focal/ui/replysorting/ReplySortingViewDelegateBinder;"
    }
}

internal val replySortingLastSelectedFinderFingerprint = fingerprint {
    strings(
        "controller_data",
        "reply_sorting_enabled",
        "reply_sorting"
    )
}
