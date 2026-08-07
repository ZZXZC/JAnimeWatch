package com.janimewatch;

public class WatchOptions {

    private boolean dub = false;
    private boolean download = false;
    private String player = "";
    private String quality = "";

    public boolean isDub() {
        return dub;
    }

    public void setDub(boolean dub) {
        this.dub = dub;
    }

    public boolean isDownload() {
        return download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }
}
