package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;
import okhttp3.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Fetches a URL and extracts readable text content by stripping HTML tags.
 */
public class WebFetchTool implements Tool {
    private final OkHttpClient client;
    private static final int MAX_CONTENT_LENGTH = 50000;

    public WebFetchTool() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    @Override
    public String getName() { return "web_fetch"; }

    @Override
    public String getDescription() {
        return "Fetch a URL and extract readable text content. Strips HTML tags and returns plain text. " +
                "Useful for reading web pages, documentation, articles, etc.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        if (url == null || url.isEmpty()) {
            return ToolResult.failure("Missing required argument: url");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (compatible; OpenCodeJava/1.0)")
                    .addHeader("Accept", "text/html,application/xhtml+xml,text/plain")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.failure("Fetch failed with status: " + response.code());
                }

                String body = response.body().string();
                String contentType = response.header("Content-Type", "");

                String text;
                if (contentType.contains("text/plain")) {
                    text = body;
                } else {
                    text = extractReadableText(body);
                }

                // Truncate if too long
                if (text.length() > MAX_CONTENT_LENGTH) {
                    text = text.substring(0, MAX_CONTENT_LENGTH) +
                            "\n\n[... truncated, " + (body.length() - MAX_CONTENT_LENGTH) + " more chars]";
                }

                return ToolResult.success("Content from " + url + ":\n\n" + text);
            }
        } catch (IOException e) {
            return ToolResult.failure("Fetch failed: " + e.getMessage());
        }
    }

    private String extractReadableText(String html) {
        // Remove script and style blocks
        String cleaned = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(html).replaceAll("");
        cleaned = Pattern.compile("<style[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(cleaned).replaceAll("");
        cleaned = Pattern.compile("<nav[^>]*>.*?</nav>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(cleaned).replaceAll("");
        cleaned = Pattern.compile("<footer[^>]*>.*?</footer>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(cleaned).replaceAll("");

        // Convert common block elements to newlines
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?i)</p>", "\n\n");
        cleaned = cleaned.replaceAll("(?i)</div>", "\n");
        cleaned = cleaned.replaceAll("(?i)</h[1-6]>", "\n\n");
        cleaned = cleaned.replaceAll("(?i)</li>", "\n");
        cleaned = cleaned.replaceAll("(?i)</tr>", "\n");

        // Strip all remaining HTML tags
        cleaned = cleaned.replaceAll("<[^>]+>", "");

        // Decode common entities
        cleaned = cleaned.replaceAll("&amp;", "&");
        cleaned = cleaned.replaceAll("&lt;", "<");
        cleaned = cleaned.replaceAll("&gt;", ">");
        cleaned = cleaned.replaceAll("&quot;", "\"");
        cleaned = cleaned.replaceAll("&#39;", "'");
        cleaned = cleaned.replaceAll("&nbsp;", " ");
        cleaned = cleaned.replaceAll("&#\\d+;", "");
        cleaned = cleaned.replaceAll("&\\w+;", "");

        // Collapse multiple whitespace/newlines
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n");
        cleaned = cleaned.trim();

        return cleaned;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("url", Map.of("type", "string", "description", "URL to fetch and extract text from"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return true; }
}
