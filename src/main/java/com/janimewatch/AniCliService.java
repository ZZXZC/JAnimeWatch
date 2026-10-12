package com.janimewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    public boolean isGitBashAvailable() {
        if (!IS_WINDOWS) return true;
        String[] candidates = {
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            System.getenv("USERPROFILE") + "\\scoop\\apps\\git\\current\\bin\\bash.exe"
        };
        for (String path : candidates) {
            if (path != null && new java.io.File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    public boolean isAniCliInstalled() {
        return commandExists("ani-cli");
    }

    public boolean isAniSkipInstalled() {
        return commandExists("ani-skip");
    }

    public boolean isScoopAvailable() {
        try {
            String shim = System.getenv("USERPROFILE") + "\\scoop\\shims\\scoop.cmd";
            if (new java.io.File(shim).exists()) {
                return true;
            }
            return commandExists("scoop");
        } catch (Exception e) {
            return false;
        }
    }

    private Process runBash(String script) throws IOException {
        List<String> cmd = IS_WINDOWS
            ? List.of(findBash(), "-c", script)
            : List.of("bash", "-c", script);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private boolean commandExists(String name) {
        try {
            Process p = runBash("command -v " + name + " >/dev/null 2>&1; exit $?");
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String stripAnsi(String line) {
        return line.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "")
                   .replaceAll("\u001B\\].*?\u0007", "")
                   .replaceAll("\u001B\\[\\?\\d+[a-zA-Z]", "")
                   .replaceAll("\u001B[()][AB012]", "")
                   .replaceAll("\r", "");
    }

    private static String shq(String s) {
        if (s == null) s = "";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private Process execAndStream(String script, Consumer<String> onStatus) {
        try {
            Process p = runBash(script);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onStatus != null) onStatus.accept(stripAnsi(line));
                    }
                } catch (IOException ignored) {
                } finally {
                    try { reader.close(); } catch (IOException ignored) {}
                }
            });
            t.setDaemon(true);
            t.start();
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    public Process installDependencies(Consumer<String> onStatus) {
        String script = String.join("\n",
            "scoop bucket add extras >/dev/null 2>&1 || true",
            "scoop install git fzf mpv ffmpeg yt-dlp aria2 ani-cli 2>&1");
        return execAndStream(script, onStatus);
    }

    public Process installAniSkip(Consumer<String> onStatus) {
        String script = "curl -sL https://raw.githubusercontent.com/synacktraa/ani-skip/master/install | sh 2>&1";
        return execAndStream(script, onStatus);
    }

    public Process play(WatchOptions options, int episode,
                        String query, int selectNth, Consumer<String> onStatus) {
        List<String> args = new ArrayList<>();
        args.add("ani-cli");
        args.add("--exit-after-play");

        if (options != null && options.isSkip()) {
            args.add("--skip");
        }

        args.add("-e");
        args.add(String.valueOf(episode));

        String quality = options == null ? "" : options.getQuality();
        if (quality != null && !quality.isEmpty() && !"best".equals(quality)) {
            args.add("-q");
            args.add(quality);
        }

        if (options != null && options.isDub()) {
            args.add("--dub");
        }

        if (options != null && "vlc".equals(options.getPlayer())) {
            args.add("-v");
        }

        args.add("-S");
        args.add(String.valueOf(selectNth));
        if (query != null && !query.isEmpty()) {
            args.add(query);
        }

        StringBuilder script = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) script.append(' ');
            script.append(shq(args.get(i)));
        }

        return execAndStream(script.toString(), onStatus);
    }
}