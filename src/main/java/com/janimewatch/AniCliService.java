package com.janimewatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

    public boolean isAniCliInstalled() {
        try {
            List<String> cmd = IS_WINDOWS
                ? List.of(findBash(), "-c", "command -v ani-cli")
                : List.of("which", "ani-cli");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
