/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public enum InstagramButtonStyleEnum {
    PRIMARY("PRIMARY"),
    SECONDARY("SECONDARY"),
    PRIMARY_LINK("PRIMARY_LINK"),
    SECONDARY_LINK("SECONDARY_LINK"),
    SECONDARY_DESTRUCTIVE("SECONDARY_DESTRUCTIVE"),
    PRIMARY_ON_COLOR("PRIMARY_ON_COLOR"),
    LABEL_INVERTED_ON_MEDIA("LABEL_INVERTED_ON_MEDIA"),
    SUPER_PRIMARY("SUPER_PRIMARY"),
    ALWAYS_WHITE("ALWAYS_WHITE"),
    TRANSPARENT_ON_DARK_COLOR("TRANSPARENT_ON_DARK_COLOR"),
    TRANSPARENT_ON_LIGHT_COLOR("TRANSPARENT_ON_LIGHT_COLOR"),
    TRANSPARENT_ON_WHITE("TRANSPARENT_ON_WHITE"),
    UNKNOWN("UNKNOWN");

    public final String name;

    private InstagramButtonStyleEnum(String name) {
        this.name = name;
    }

}
