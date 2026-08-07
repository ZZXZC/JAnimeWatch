package com.janimewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AniCliService {

    private static final String[] UPDATE_CMD = {"ani-cli", "-U"};

    public boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ani-cli", "--help");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<List<String>> searchAnime(String query, Consumer<String> onOutput) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            try {
                ProcessBuilder pb = new ProcessBuilder("ani-cli", query);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        results.add(line);
                        if (onOutput != null) {
                            onOutput.accept(line);
                        }
                    }
                }
                p.waitFor();
            } catch (IOException | InterruptedException e) {
                results.add("ERROR: " + e.getMessage());
            }
            return results;
        });
    }

    public CompletableFuture<Integer> watchAnime(String animeQuery, int episode, WatchOptions opts, Consumer<String> onOutput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add("ani-cli");
                cmd.add(animeQuery);

                if (episode > 0) {
                    cmd.add("-e");
                    cmd.add(String.valueOf(episode));
                }
                if (opts.isDub()) {
                    cmd.add("--dub");
                }
                if (opts.getPlayer() != null && !opts.getPlayer().isEmpty()) {
                    cmd.add("--" + opts.getPlayer());
                }
                if (opts.getQuality() != null && !opts.getQuality().isEmpty()) {
                    cmd.add("-q");
                    cmd.add(opts.getQuality());
                }
                if (opts.isDownload()) {
                    cmd.add("-d");
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onOutput != null) {
                            onOutput.accept(line);
                        }
                    }
                }
                return p.waitFor();
            } catch (IOException | InterruptedException e) {
                if (onOutput != null) {
                    onOutput.accept("ERROR: " + e.getMessage());
                }
                return -1;
            }
        });
    }

    public CompletableFuture<Integer> update(Consumer<String> onOutput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(UPDATE_CMD);
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onOutput != null) {
                            onOutput.accept(line);
                        }
                    }
                }
                return p.waitFor();
            } catch (IOException | InterruptedException e) {
                if (onOutput != null) {
                    onOutput.accept("ERROR: " + e.getMessage());
                }
                return -1;
            }
        });
    }
}
