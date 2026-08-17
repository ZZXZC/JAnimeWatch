package com.janimewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class AllAnimeService {

    private static final String BASE_URL = "https://anidb.app";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private String stripAnsi(String line) {
        return line.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "")
                   .replaceAll("\u001B\\].*?\u0007", "")
                   .replaceAll("\u001B\\[\\?\\d+[a-zA-Z]", "")
                   .replaceAll("\u001B[()][AB012]", "")
                   .replaceAll("\r", "");
    }

    private String curl(String url) {
        return curl(url, null);
    }

    private String curl(String url, String referer) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("curl"); cmd.add("-sL");
            cmd.add("-A"); cmd.add(USER_AGENT);
            cmd.add("-H"); cmd.add("Accept: application/json, text/html, */*");
            if (referer != null) {
                cmd.add("-H"); cmd.add("Referer: " + referer);
            }
            cmd.add("--max-time"); cmd.add("20");
            cmd.add(url);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractNumericId(String slugOrId) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("([0-9]+)$").matcher(slugOrId);
        return m.find() ? m.group(1) : slugOrId;
    }

    public List<AnimeResult> search(String query, Consumer<String> onStatus) {
        List<AnimeResult> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        String url = BASE_URL + "/browse?q=" + encoded;

        if (onStatus != null) onStatus.accept("Searching anidb.app...");
        String html = curl(url);

        if (html.isEmpty()) {
            if (onStatus != null) onStatus.accept("Connection failed");
            return results;
        }

        // Match: <a href="...anime/one-piece-3880" ... title="One Piece">
        Pattern pattern = Pattern.compile("anime/([a-z0-9][a-z0-9\\-]*-[0-9]+)\"[^>]*?title=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String id = matcher.group(1);
            String name = matcher.group(2)
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
            results.add(new AnimeResult(id, name));
        }

        if (onStatus != null) onStatus.accept("Found " + results.size() + " results");
        return results;
    }

    public List<Integer> getEpisodes(String animeId, Consumer<String> onStatus) {
        List<Integer> episodes = new ArrayList<>();
        String numId = extractNumericId(animeId);
        String referer = BASE_URL + "/anime/" + animeId;
        String url = BASE_URL + "/api/frontend/anime/" + numId + "/episodes";

        if (onStatus != null) onStatus.accept("Loading episodes...");
        String json = curl(url, referer);

        if (json.isEmpty()) {
            if (onStatus != null) onStatus.accept("Failed to load episodes");
            return episodes;
        }

        Pattern pattern = Pattern.compile("\"number\":([0-9]+)");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            try {
                int ep = Integer.parseInt(matcher.group(1));
                if (!episodes.contains(ep)) {
                    episodes.add(ep);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (onStatus != null) onStatus.accept("Found " + episodes.size() + " episodes");
        return episodes;
    }

    public String getStreamUrl(String animeId, int episode, String quality, String mode, Consumer<String> onStatus) {
        String numId = extractNumericId(animeId);
        String referer = BASE_URL + "/anime/" + animeId;

        // Get episodes list to find the episode ID
        String epUrl = BASE_URL + "/api/frontend/anime/" + numId + "/episodes";
        if (onStatus != null) onStatus.accept("Finding episode " + episode + "...");
        String epJson = curl(epUrl, referer);

        Pattern epPattern = Pattern.compile("\"id\":([0-9]+),\"number\":" + episode);
        Matcher epMatcher = epPattern.matcher(epJson);
        if (!epMatcher.find()) {
            // Try alternative pattern
            epPattern = Pattern.compile("\"number\":" + episode + ",\"id\":([0-9]+)");
            epMatcher = epPattern.matcher(epJson);
            if (!epMatcher.find()) {
                if (onStatus != null) onStatus.accept("Episode " + episode + " not found");
                return null;
            }
        }
        String episodeId = epMatcher.group(1);

        // Get language/stream info
        String langUrl = BASE_URL + "/api/frontend/episode/" + episodeId + "/languages";
        if (onStatus != null) onStatus.accept("Getting stream sources...");
        String langJson = curl(langUrl, referer);

        // Extract embed URL - prefer sub (jpn) or dub (eng) based on mode
        String langCode = "dub".equals(mode) ? "eng" : "jpn";
        Pattern embedPattern = Pattern.compile("\"" + langCode + "\".*?\"embed_url\":\"([^\"]+)\"");
        Matcher embedMatcher = embedPattern.matcher(langJson);
        if (!embedMatcher.find()) {
            // Fallback: try the other language
            langCode = "dub".equals(mode) ? "jpn" : "eng";
            embedPattern = Pattern.compile("\"" + langCode + "\".*?\"embed_url\":\"([^\"]+)\"");
            embedMatcher = embedPattern.matcher(langJson);
            if (!embedMatcher.find()) {
                if (onStatus != null) onStatus.accept("No stream sources found");
                return null;
            }
        }

        String embedUrl = embedMatcher.group(1).replace("\\/", "/");
        if (onStatus != null) onStatus.accept("Resolving stream URL...");

        // Get the embed page to find m3u8 master URL
        String embedPage = curl(embedUrl);
        Pattern m3u8Pattern = Pattern.compile("file:\\s*'([^']+\\.m3u8[^']*)'");
        Matcher m3u8Matcher = m3u8Pattern.matcher(embedPage);
        if (!m3u8Matcher.find()) {
            if (onStatus != null) onStatus.accept("Could not resolve stream URL");
            return null;
        }

        String masterUrl = m3u8Matcher.group(1);
        if (onStatus != null) onStatus.accept("Selecting quality...");

        // Get master playlist to find quality-specific URL
        String masterPlaylist = curl(masterUrl);
        if (quality == null || quality.isEmpty() || quality.equals("best")) {
            // Return the master URL and let mpv handle quality selection
            return masterUrl;
        }

        // Parse m3u8 for specific quality
        String[] lines = masterPlaylist.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].contains(quality) && lines[i + 1].trim().startsWith("http")) {
                return lines[i + 1].trim();
            }
            // Also check for resolution tag matching quality
            if (lines[i].contains("RESOLUTION") && lines[i].contains(quality.replace("p", ""))) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    if (!nextLine.startsWith("#")) {
                        // Build full URL if relative
                        if (nextLine.startsWith("http")) {
                            return nextLine;
                        }
                        String masterBase = masterUrl.substring(0, masterUrl.lastIndexOf("/") + 1);
                        return masterBase + nextLine;
                    }
                }
            }
        }

        // Fallback to master URL
        return masterUrl;
    }

    public Process launchPlayer(String url, String title, String player, boolean download) {
        try {
            List<String> cmd = new ArrayList<>();

            if (download) {
                // Try yt-dlp first, fallback to ffmpeg
                cmd.add("yt-dlp");
                cmd.add(url);
                cmd.add("--no-skip-unavailable-fragments");
                cmd.add("--fragment-retries");
                cmd.add("infinite");
                cmd.add("-N");
                cmd.add("16");
                cmd.add("-o");
                cmd.add(title + ".mp4");
            } else if ("vlc".equals(player)) {
                cmd.add("vlc");
                cmd.add("--play-and-exit");
                cmd.add("--meta-title=" + title);
                cmd.add(url);
            } else {
                // mpv
                cmd.add("mpv.exe");
                cmd.add("--force-media-title=" + title);
                cmd.add(url);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            return pb.start();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean isPlayerInstalled(String player) {
        String cmd = "vlc".equals(player) ? "vlc" : "mpv.exe";
        try {
            ProcessBuilder pb = new ProcessBuilder("where", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCurlInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("where", "curl");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
