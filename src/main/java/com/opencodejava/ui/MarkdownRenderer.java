package com.opencodejava.ui;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Renders markdown text with ANSI terminal formatting.
 * Provides basic syntax highlighting for code blocks, bold, italic, etc.
 */
public class MarkdownRenderer {
    private final Parser parser;

    public MarkdownRenderer() {
        this.parser = Parser.builder().build();
    }

    /**
     * Render markdown content to terminal-formatted text.
     * For now, does basic transformations: bold, code, headers.
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        StringBuilder output = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) {
                    String lang = line.substring(3).trim();
                    output.append(colorize("┌─ " + (lang.isEmpty() ? "code" : lang) + " ", AttributedStyle.BRIGHT));
                    output.append(colorize("─".repeat(40), AttributedStyle.BRIGHT));
                    output.append("\n");
                } else {
                    output.append(colorize("└" + "─".repeat(50), AttributedStyle.BRIGHT));
                    output.append("\n");
                }
                continue;
            }

            if (inCodeBlock) {
                output.append(colorize("│ ", AttributedStyle.BRIGHT));
                output.append(colorize(line, AttributedStyle.GREEN));
                output.append("\n");
                continue;
            }

            // Headers
            if (line.startsWith("### ")) {
                output.append(colorize("   " + line.substring(4), AttributedStyle.YELLOW));
                output.append("\n");
            } else if (line.startsWith("## ")) {
                output.append(colorize("  " + line.substring(3), AttributedStyle.CYAN));
                output.append("\n");
            } else if (line.startsWith("# ")) {
                output.append(colorize(line.substring(2), AttributedStyle.MAGENTA));
                output.append("\n");
            } else {
                // Inline formatting
                output.append(renderInline(line));
                output.append("\n");
            }
        }

        return output.toString();
    }

    private String renderInline(String text) {
        // Bold: **text**
        text = text.replaceAll("\\*\\*(.+?)\\*\\*",
                "\033[1m$1\033[0m");
        // Italic: *text*
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)",
                "\033[3m$1\033[0m");
        // Inline code: `text`
        text = text.replaceAll("`(.+?)`",
                "\033[32m$1\033[0m");
        // Links: [text](url) -> text (url)
        text = text.replaceAll("\\[(.+?)\\]\\((.+?)\\)",
                "$1 (\033[4;34m$2\033[0m)");
        return text;
    }

    private String colorize(String text, int color) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(color))
                .append(text)
                .style(AttributedStyle.DEFAULT)
                .toAnsi();
    }
}
