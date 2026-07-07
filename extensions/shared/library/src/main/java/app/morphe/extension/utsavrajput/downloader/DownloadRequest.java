/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

public class DownloadRequest {
    public String url;
    public String subFolder; 
    public String fileName;

    public DownloadRequest(String url, String subFolder, String fileName) {
        this.url = url;
        this.subFolder = subFolder;
        this.fileName = fileName;
    }
}