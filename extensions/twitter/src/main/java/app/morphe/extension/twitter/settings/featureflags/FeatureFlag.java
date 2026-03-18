/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.settings.featureflags;

import java.io.Serializable;
import java.util.ArrayList;

public class FeatureFlag implements Serializable {
    private final String name;
    private final Boolean enabled;

    public FeatureFlag(String name, Boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public static String toStringPref(ArrayList<FeatureFlag> flags) {
        StringBuilder out = new StringBuilder();
        for (FeatureFlag flag: flags) {
            out.append(flag.name).append(":");
            out.append(flag.enabled).append(",");
        }
        return out.toString();
    }
}
