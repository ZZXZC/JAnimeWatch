package com.janimewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AniCliService {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");

    private String findBash() {
        String[] candidates = {
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            System.getenv("USERPROFILE") + "\\scoop\\apps\\git\\current\\bin\\bash.exe"
        };
        for (String path : candidates) {
            if (path != null && new java.io.File(path).exists()) {
                return path;
            }
        }
        return "bash";
    }

    private String findAniCli() {
        String scoopShims = System.getenv("USERPROFILE") + "\\scoop\\shims\\ani-cli";
        if (new java.io.File(scoopShims).exists()) {
            return scoopShims;
        }
        return "ani-cli";
    }

    private List<String> buildCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) {
            cmd.add(findBash());
            cmd.add(findAniCli());
        } else {
            cmd.add("ani-cli");
        }
        for (String arg : args) {
            cmd.add(arg);
        }
        return cmd;
    }

    public boolean isInstalled() {
        try {
            List<String> cmd = IS_WINDOWS
                ? List.of(findBash(), findAniCli(), "--help")
                : List.of("ani-cli", "--help");
            ProcessBuilder pb = new ProcessBuilder(cmd);
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
                List<String> cmd = buildCommand(query);
                ProcessBuilder pb = new ProcessBuilder(cmd);
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
                List<String> args = new ArrayList<>();
                args.add(animeQuery);

                if (episode > 0) {
                    args.add("-e");
                    args.add(String.valueOf(episode));
                }
                if (opts.isDub()) {
                    args.add("--dub");
                }
                if (opts.getPlayer() != null && !opts.getPlayer().isEmpty()) {
                    args.add("--" + opts.getPlayer());
                }
                if (opts.getQuality() != null && !opts.getQuality().isEmpty()) {
                    args.add("-q");
                    args.add(opts.getQuality());
                }
                if (opts.isDownload()) {
                    args.add("-d");
                }

                List<String> cmd = buildCommand(args.toArray(new String[0]));
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
                List<String> cmd = buildCommand("-U");
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
}
