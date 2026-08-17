package com.janimewatch;

public class HistoryEntry {
    private final int episode;
    private final String animeId;
    private final String animeName;

    public HistoryEntry(int episode, String animeId, String animeName) {
        this.episode = episode;
        this.animeId = animeId;
        this.animeName = animeName;
    }

    public int getEpisode() { return episode; }
    public String getAnimeId() { return animeId; }
    public String getAnimeName() { return animeName; }

    public String toLine() {
        return episode + "\t" + animeId + "\t" + animeName;
    }

    public static HistoryEntry fromLine(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length < 3) return null;
        try {
            return new HistoryEntry(Integer.parseInt(parts[0]), parts[1], parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return animeName + " — Episode " + episode;
    }
}
