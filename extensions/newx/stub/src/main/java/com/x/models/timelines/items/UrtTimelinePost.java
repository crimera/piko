/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package com.x.models.timelines.items;

import com.x.models.ClientEventInfo;
import com.x.models.PostIdentifier;
import com.x.models.PostResult;
import com.x.models.TimelinePromotedMetadata;
import com.x.models.UserResult;
import com.x.models.articles.Article;
import com.x.models.cards.LegacyCard;
import com.x.models.notes.NotePost;

public abstract class UrtTimelinePost implements UrtTimelineItem {

    @Override
    public String getEntryId() {
        return null;
    }

    @Override
    public ClientEventInfo getClientEventInfo() {
        return null;
    }

    public abstract TimelinePromotedMetadata getPromotedMetadata();

    public abstract PostResult getPostResult();

    public abstract String getText();

    public abstract UserResult getAuthor();

    public abstract PostIdentifier getId();

    public abstract NotePost getNotePost();

    public abstract Article getArticle();

    public abstract LegacyCard getLegacyCard();
}
