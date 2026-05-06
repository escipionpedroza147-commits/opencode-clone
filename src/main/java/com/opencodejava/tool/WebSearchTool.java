package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search tool using DuckDuckGo HTML search (no API key needed).
 * Parses results and returns top 5 titles + URLs + snippets.
 */
public class WebSearchTool implements Tool {
    private final OkHttpClient client;

    public WebSearchTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    @Override
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the web using DuckDuckGo. Returns top 5 results with titles, URLs, and snippets.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.isEmpty()) {
            return ToolResult.failure("Missing required argument: query");
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (compatible; OpenCodeJava/1.0)")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.failure("Search request failed with status: " + response.code());
                }

                String html = response.body().string();
                List<SearchResult> results = parseResults(html);

                if (results.isEmpty()) {
                    return ToolResult.success("No results found for: " + query);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Search results for: ").append(query).append("\n\n");
                int count = 0;
                for (SearchResult result : results) {
                    if (count >= 5) break;
                    count++;
                    sb.append(count).append(". ").append(result.title).append("\n");
                    sb.append("   URL: ").append(result.url).append("\n");
                    if (result.snippet != null && !result.snippet.isEmpty()) {
                        sb.append("   ").append(result.snippet).append("\n");
                    }
                    sb.append("\n");
                }

                return ToolResult.success(sb.toString());
            }
        } catch (IOException e) {
            return ToolResult.failure("Search failed: " + e.getMessage());
        }
    }

    private List<SearchResult> parseResults(String html) {
        List<SearchResult> results = new ArrayList<>();

        // Pattern to match DuckDuckGo result blocks
        Pattern resultPattern = Pattern.compile(
                "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.CASE_INSENSITIVE
        );
        Pattern snippetPattern = Pattern.compile(
                "<a[^>]*class=\"result__snippet\"[^>]*>([^<]*(?:<[^>]*>[^<]*)*)</a>",
                Pattern.CASE_INSENSITIVE
        );

        Matcher resultMatcher = resultPattern.matcher(html);
        Matcher snippetMatcher = snippetPattern.matcher(html);

        while (resultMatcher.find() && results.size() < 5) {
            String url = resultMatcher.group(1);
            String title = resultMatcher.group(2).trim();

            // Decode DuckDuckGo redirect URL
            if (url.contains("uddg=")) {
                try {
                    String decoded = java.net.URLDecoder.decode(
                            url.substring(url.indexOf("uddg=") + 5), StandardCharsets.UTF_8);
                    if (decoded.contains("&")) {
                        decoded = decoded.substring(0, decoded.indexOf("&"));
                    }
                    url = decoded;
                } catch (Exception ignored) {}
            }

            String snippet = "";
            if (snippetMatcher.find()) {
                snippet = stripHtml(snippetMatcher.group(1)).trim();
            }

            if (!title.isEmpty()) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        return results;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ").trim();
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", Map.of("type", "string", "description", "Search query"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return true; }

    private record SearchResult(String title, String url, String snippet) {}
}
