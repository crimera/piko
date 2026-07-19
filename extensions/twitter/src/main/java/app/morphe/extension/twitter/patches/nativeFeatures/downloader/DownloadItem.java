/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.downloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.twitter.entity.Media;

public class DownloadItem {

    public String labelText;
    public String fileName;
    public Media media;
    public List<DownloadItem> variants;

    public DownloadItem(String labelText, String fileName, Media media, List<DownloadItem> variants) {
        this.labelText = labelText;
        this.fileName = fileName;
        this.media = media;
        this.variants = variants;
    }

    public DownloadItem(String labelText, String fileName, Media media) {
        this(labelText,fileName,media,Collections.<DownloadItem>emptyList());
    }

    public boolean hasVariants() {
return !variants.isEmpty() && variants.size()>1;
    }
}