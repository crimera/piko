/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package com.x.models.timelines.items;

public abstract class UrtTimelineModuleItem {
    public abstract UrtTimelineItem getItem();

    public abstract boolean isDispensable();

    public abstract UrtTimelineModuleItem copy(UrtTimelineItem item, boolean isDispensable);
}
