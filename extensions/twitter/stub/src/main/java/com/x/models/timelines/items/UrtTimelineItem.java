/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package com.x.models.timelines.items;

import com.x.models.ClientEventInfo;

public interface UrtTimelineItem {

    long getSortIndex();

    String getEntryId();

    ClientEventInfo getClientEventInfo();
}
