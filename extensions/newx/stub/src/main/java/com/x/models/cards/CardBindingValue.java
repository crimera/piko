package com.x.models.cards;

public abstract class CardBindingValue {
    public abstract static class StringValue extends CardBindingValue {
        public abstract String getValue();
    }
}
