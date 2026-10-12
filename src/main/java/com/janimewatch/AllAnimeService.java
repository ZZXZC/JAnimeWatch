package com.janimewatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AllAnimeService {

    private static final String BASE_URL = "https://hianime.at";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String EMBED_KEY = "otaku-embed-v1";

    public static class StreamResult {
        public final String url;
        public final String referrer;
        public final String subtitle;

        public StreamResult(String url, String referrer, String subtitle) {
            this.url = url;
            this.referrer = referrer;
            this.subtitle = subtitle;
        }
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

    private boolean looksBlocked(String html) {
        return html.contains("Just a moment");
    }

    private String unescape(String s) {
        return s.replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
    }

    private String slugSuffix(String animeId) {
        int i = animeId.lastIndexOf('-');
        return i >= 0 ? animeId.substring(i + 1) : animeId;
    }

    public List<AnimeResult> search(String query, Consumer<String> onStatus) {
        List<AnimeResult> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        String url = BASE_URL + "/search?keyword=" + encoded;

        if (onStatus != null) onStatus.accept("Searching hianime.at...");
        String html = curl(url);

        if (html.isEmpty()) {
            if (onStatus != null) onStatus.accept("Connection failed");
            return results;
        }
        if (looksBlocked(html)) {
            if (onStatus != null) onStatus.accept("Blocked by cloudflare - try again later");
            return results;
        }

        int cut = html.indexOf("id=\"main-sidebar\"");
        if (cut >= 0) {
            html = html.substring(0, cut);
        }
        String flat = html.replaceAll("[\\r\\n]+", " ");

        Pattern pattern = Pattern.compile(
            "<h3 class=\"film-name\">\\s*<a href=\"[^\"]*/([^\"/]+)\"\\s*title=\"([^\"]+)\"");
        for (String chunk : flat.split(Pattern.quote("<div class=\"film-detail\">"), -1)) {
            Matcher matcher = pattern.matcher(chunk);
            if (matcher.find()) {
                results.add(new AnimeResult(unescape(matcher.group(1)), unescape(matcher.group(2))));
            }
        }

        if (onStatus != null) onStatus.accept("Found " + results.size() + " results");
        return results;
    }

    private Map<Integer, String> episodeMap(String animeId, Consumer<String> onStatus) {
        Map<Integer, String> map = new LinkedHashMap<>();
        String url = BASE_URL + "/api/theme/episode/list/" + slugSuffix(animeId);

        if (onStatus != null) onStatus.accept("Loading episodes...");
        String json = curl(url);

        if (json.isEmpty()) {
            if (onStatus != null) onStatus.accept("Failed to load episodes");
            return map;
        }

        String flat = json.replace("\\", "");
        Pattern pattern = Pattern.compile(
            "data-number=\"([^\"]+)\".*?data-id=\"([0-9]+)\".*?/watch/"
                + Pattern.quote(animeId) + "\\?ep=",
            Pattern.DOTALL);
        Matcher matcher = pattern.matcher(flat);
        while (matcher.find()) {
            try {
                int num = Integer.parseInt(matcher.group(1));
                map.putIfAbsent(num, matcher.group(2));
            } catch (NumberFormatException ignored) {
            }
        }

        if (onStatus != null) onStatus.accept("Found " + map.size() + " episodes");
        return map;
    }

    public List<Integer> getEpisodes(String animeId, Consumer<String> onStatus) {
        return new ArrayList<>(episodeMap(animeId, onStatus).keySet());
    }

    private String findZokoHash(String flat, String type) {
        Pattern hashPattern = Pattern.compile("data-hash=\"([^\"]+)\"");
        for (String chunk : flat.split(Pattern.quote("server-item"), -1)) {
            if (chunk.contains("data-type=\"" + type + "\"")
                && chunk.contains("data-server-name=\"ZokoAnime\"")) {
                Matcher matcher = hashPattern.matcher(chunk);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private String base64Decode(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            try {
                return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private String deobfuscate(String blob) {
        try {
            byte[] bytes = Base64.getDecoder().decode(blob);
            byte[] key = EMBED_KEY.getBytes(StandardCharsets.US_ASCII);
            byte[] out = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                out[i] = (byte) (bytes[i] ^ key[i % key.length]);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String originOf(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return null;
        int pathStart = url.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) return url + "/";
        return url.substring(0, pathStart + 1);
    }

    private String extractSubtitle(String json) {
        int start = json.indexOf("\"subtitles\":[");
        if (start < 0) return null;
        int end = json.indexOf(']', start);
        if (end < 0) return null;
        for (String entry : json.substring(start, end).split("\\},")) {
            if (entry.contains("\"default\":true")) {
                Matcher m = Pattern.compile("\"src\":\"([^\"]+)\"").matcher(entry);
                if (m.find()) {
                    return m.group(1).replace("\\/", "/");
                }
            }
        }
        return null;
    }

    private String pickQuality(String masterUrl, String playlist, String quality) {
        String[] lines = playlist.split("\n");
        String base = masterUrl.substring(0, masterUrl.lastIndexOf('/') + 1);
        String fallback = null;
        Pattern resPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;

            Matcher rm = resPattern.matcher(line);
            if (!rm.find()) continue;
            String height = rm.group(2);

            String variant = null;
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j].trim();
                if (next.isEmpty()) continue;
                if (next.startsWith("#")) break;
                variant = next;
                break;
            }
            if (variant == null) continue;
            if (!variant.startsWith("http")) {
                variant = base + variant;
            }
            if (height.equals(quality)) {
                return variant;
            }
            if (fallback == null) {
                fallback = variant;
            }
        }
        return fallback;
    }

    public StreamResult getStreamUrl(String animeId, int episode, String quality, String mode,
                                     Consumer<String> onStatus) {
        Map<Integer, String> episodes = episodeMap(animeId, onStatus);
        String epId = episodes.get(episode);
        if (epId == null) {
            if (onStatus != null) onStatus.accept("Episode " + episode + " not found");
            return null;
        }

        if (onStatus != null) onStatus.accept("Getting stream sources...");
        String serversJson = curl(BASE_URL + "/api/theme/episode/servers?episodeId=" + epId);
        if (serversJson.isEmpty()) {
            if (onStatus != null) onStatus.accept("No stream sources found");
            return null;
        }
        String flat = serversJson.replace("\\", "");

        String wanted = "dub".equals(mode) ? "dub" : "sub";
        String hash = findZokoHash(flat, wanted);
        if (hash == null) {
            hash = findZokoHash(flat, "dub".equals(mode) ? "sub" : "dub");
        }
        if (hash == null) {
            if (onStatus != null) onStatus.accept("No stream sources found for " + mode);
            return null;
        }

        String embed = base64Decode(hash);
        if (embed == null || embed.isEmpty()) {
            if (onStatus != null) onStatus.accept("Could not resolve stream URL");
            return null;
        }
        String referrer = originOf(embed);

        if (onStatus != null) onStatus.accept("Resolving stream URL...");
        String embedPage = curl(embed);
        Matcher pm = Pattern.compile("window\\.__P=\"([^\"]*)\"").matcher(embedPage);
        if (!pm.find()) {
            if (onStatus != null) onStatus.accept("Could not resolve stream URL");
            return null;
        }

        String json = deobfuscate(pm.group(1));
        if (json == null) {
            if (onStatus != null) onStatus.accept("Could not resolve stream URL");
            return null;
        }

        Matcher sm = Pattern.compile("\"src\":\"([^\"]*\\.m3u8[^\"]*)\"").matcher(json);
        if (!sm.find()) {
            if (onStatus != null) onStatus.accept("Could not resolve stream URL");
            return null;
        }
        String masterUrl = sm.group(1).replace("\\/", "/");
        String subtitle = extractSubtitle(json);

        if (quality == null || quality.isEmpty() || quality.equals("best")) {
            return new StreamResult(masterUrl, referrer, subtitle);
        }

        if (onStatus != null) onStatus.accept("Selecting quality...");
        String playlist = curl(masterUrl, referrer);
        String chosen = playlist.isEmpty() ? null : pickQuality(masterUrl, playlist, quality);
        if (chosen == null) {
            if (onStatus != null) onStatus.accept(quality + " not found, using best");
            chosen = masterUrl;
        }
        return new StreamResult(chosen, referrer, subtitle);
    }

    public Process launchPlayer(String url, String title, String player, boolean download,
                                String referrer, String subtitle) {
        try {
            List<String> cmd = new ArrayList<>();

            if (download) {
                cmd.add("yt-dlp");
                if (referrer != null && !referrer.isEmpty()) {
                    cmd.add("--referer");
                    cmd.add(referrer);
                }
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
                if (referrer != null && !referrer.isEmpty()) {
                    cmd.add("--http-referrer=" + referrer);
                }
                cmd.add(url);
                if (subtitle != null && !subtitle.isEmpty()) {
                    cmd.add(":input-slave=" + subtitle);
                }
            } else {
                cmd.add("mpv.exe");
                cmd.add("--force-media-title=" + title);
                if (referrer != null && !referrer.isEmpty()) {
                    cmd.add("--referrer=" + referrer);
                }
                if (subtitle != null && !subtitle.isEmpty()) {
                    cmd.add("--sub-file=" + subtitle);
                }
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