package com.janimewatch;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HistoryService {

    private static final Path HISTORY_DIR = Path.of(
        System.getProperty("user.home"), ".janimewatch");
    private static final Path OUR_HISTORY = HISTORY_DIR.resolve("history");

    // ani-cli history location
    private static final Path ANI_CLI_HIST = Path.of(
        System.getProperty("user.home"), ".local", "state", "ani-cli", "ani-hsts");

    public List<HistoryEntry> load() {
        Set<String> seen = new LinkedHashSet<>();
        List<HistoryEntry> entries = new ArrayList<>();

        // Load from ani-cli first (older entries)
        loadFrom(ANI_CLI_HIST, entries, seen);
        // Load our own (newer entries override)
        loadFrom(OUR_HISTORY, entries, seen);

        return entries;
    }

    private void loadFrom(Path file, List<HistoryEntry> entries, Set<String> seen) {
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;
                HistoryEntry entry = HistoryEntry.fromLine(line);
                if (entry == null) continue;
                // Dedupe by anime name (case-insensitive)
                String key = entry.getAnimeName().toLowerCase();
                if (seen.add(key)) {
                    entries.add(entry);
                }
            }
        } catch (IOException ignored) {}
    }

    public void save(int episode, String animeId, String animeName) {
        try {
            Files.createDirectories(OUR_HISTORY.getParent());
            List<HistoryEntry> entries = load();

            entries.removeIf(e ->
                e.getAnimeName().equalsIgnoreCase(animeName));
            entries.add(new HistoryEntry(episode, animeId, animeName));

            try (BufferedWriter writer = Files.newBufferedWriter(OUR_HISTORY)) {
                for (HistoryEntry e : entries) {
                    writer.write(e.toLine());
                    writer.newLine();
                }
            }
        } catch (IOException ignored) {}
    }

    public void clear() {
        try { Files.deleteIfExists(OUR_HISTORY); } catch (IOException ignored) {}
    }
}
