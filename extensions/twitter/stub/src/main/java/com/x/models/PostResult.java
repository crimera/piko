package com.x.models;

import com.x.models.articles.Article;
import com.x.models.cards.LegacyCard;
import com.x.models.notes.NotePost;

public interface PostResult {
    String getText();
    UserResult getAuthor();
    NotePost getNotePost();
    Article getArticle();
    LegacyCard getLegacyCard();
    PostIdentifier getId();
}
