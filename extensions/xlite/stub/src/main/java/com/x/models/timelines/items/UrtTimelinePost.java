/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package com.x.models.timelines.items;

import com.x.models.ClientEventInfo;
import com.x.models.TimelinePromotedMetadata;

public class UrtTimelinePost implements UrtTimelineItem {

    @Override
    public String getEntryId() {
        return null;
    }

    @Override
    public ClientEventInfo getClientEventInfo() {
        return null;
    }

    public TimelinePromotedMetadata getPromotedMetadata() {
        return null;
    }
}
