package com.x.models.timelinemodule;

import com.x.models.PostIdentifier;

import java.util.List;

public interface ModuleDisplayType {
    abstract class VerticalConversation implements ModuleDisplayType {
        public abstract List<PostIdentifier> getAllTweetIds();

        public abstract VerticalConversation copy(List<PostIdentifier> allTweetIds);
    }
}
