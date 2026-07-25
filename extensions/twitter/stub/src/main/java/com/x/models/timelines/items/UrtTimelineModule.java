package com.x.models.timelines.items;

import com.x.models.ClientEventInfo;
import com.x.models.timelinemodule.ModuleDisplayType;
import com.x.models.timelinemodule.ModuleFooter;
import com.x.models.timelinemodule.ModuleHeader;
import java.util.List;

public abstract class UrtTimelineModule implements UrtTimelineItem {
    public abstract List<UrtTimelineModuleItem> getInnerContent();
    public abstract ModuleHeader getModuleHeader();
    public abstract ModuleFooter getModuleFooter();
    public abstract ModuleDisplayType getDisplayType();
    public abstract long getSortIndex();
    public abstract UrtTimelineModule copy(
            List<UrtTimelineModuleItem> innerContent,
            ModuleHeader moduleHeader,
            ModuleFooter moduleFooter,
            ModuleDisplayType displayType,
            long sortIndex,
            String entryId,
            ClientEventInfo clientEventInfo);
}
