package com.x.models;

public abstract class ContextualPost implements PostResult {
    public abstract CanonicalPost getCanonicalPost();
    public abstract PostResult getDisplayQuotedPost();
}
