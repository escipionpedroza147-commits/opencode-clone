package com.opencodejava.ui;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders colored diffs showing file changes.
 * Green for added lines, red for removed lines.
 */
public class DiffRenderer {
    private static DiffRenderer instance;

    private DiffRenderer() {}

    public static synchronized DiffRenderer getInstance() {
        if (instance == null) {
            instance = new DiffRenderer();
        }
        return instance;
    }

    /**
     * Render a diff between old content and new content.
     * Returns a colored string showing additions and removals.
     */
    public String renderDiff(String oldContent, String newContent, String filePath) {
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";

        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        List<DiffLine> diff = computeDiff(oldLines, newLines);

        if (diff.isEmpty()) {
            return colorize("  (no changes)", AttributedStyle.YELLOW);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(colorize("  ┌─ " + filePath, AttributedStyle.CYAN)).append("\n");

        int contextLines = 3;
        List<DiffLine> significant = filterWithContext(diff, contextLines);

        for (DiffLine line : significant) {
            switch (line.type) {
                case ADDED -> sb.append(colorize("  + " + line.content, AttributedStyle.GREEN)).append("\n");
                case REMOVED -> sb.append(colorize("  - " + line.content, AttributedStyle.RED)).append("\n");
                case CONTEXT -> sb.append("    ").append(line.content).append("\n");
                case SEPARATOR -> sb.append(colorize("  ...", AttributedStyle.BRIGHT)).append("\n");
            }
        }

        sb.append(colorize("  └─", AttributedStyle.CYAN));

        // Summary
        long added = diff.stream().filter(d -> d.type == DiffType.ADDED).count();
        long removed = diff.stream().filter(d -> d.type == DiffType.REMOVED).count();
        sb.append(colorize(" +" + added, AttributedStyle.GREEN));
        sb.append(colorize(" -" + removed, AttributedStyle.RED));

        return sb.toString();
    }

    /**
     * Simple diff algorithm using longest common subsequence approach.
     */
    private List<DiffLine> computeDiff(String[] oldLines, String[] newLines) {
        List<DiffLine> result = new ArrayList<>();

        // Build LCS table
        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        // Backtrack to find diff
        List<DiffLine> reversed = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                reversed.add(new DiffLine(DiffType.CONTEXT, oldLines[i - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                reversed.add(new DiffLine(DiffType.ADDED, newLines[j - 1]));
                j--;
            } else {
                reversed.add(new DiffLine(DiffType.REMOVED, oldLines[i - 1]));
                i--;
            }
        }

        // Reverse to get correct order
        for (int k = reversed.size() - 1; k >= 0; k--) {
            result.add(reversed.get(k));
        }

        return result;
    }

    /**
     * Filter diff to only show changed lines with surrounding context.
     */
    private List<DiffLine> filterWithContext(List<DiffLine> diff, int contextSize) {
        // Mark which lines to show (changes + context around them)
        boolean[] show = new boolean[diff.size()];
        for (int i = 0; i < diff.size(); i++) {
            if (diff.get(i).type != DiffType.CONTEXT) {
                for (int j = Math.max(0, i - contextSize); j <= Math.min(diff.size() - 1, i + contextSize); j++) {
                    show[j] = true;
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        boolean lastWasShown = false;
        for (int i = 0; i < diff.size(); i++) {
            if (show[i]) {
                if (!lastWasShown && !result.isEmpty()) {
                    result.add(new DiffLine(DiffType.SEPARATOR, ""));
                }
                result.add(diff.get(i));
                lastWasShown = true;
            } else {
                lastWasShown = false;
            }
        }

        // Limit output to prevent overwhelming the terminal
        if (result.size() > 50) {
            List<DiffLine> truncated = new ArrayList<>(result.subList(0, 50));
            long totalAdded = diff.stream().filter(d -> d.type == DiffType.ADDED).count();
            long totalRemoved = diff.stream().filter(d -> d.type == DiffType.REMOVED).count();
            truncated.add(new DiffLine(DiffType.SEPARATOR, 
                    "[... " + (result.size() - 50) + " more diff lines, total +" + totalAdded + " -" + totalRemoved + "]"));
            return truncated;
        }

        return result;
    }

    private String colorize(String text, int color) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(color))
                .append(text)
                .style(AttributedStyle.DEFAULT)
                .toAnsi();
    }

    private enum DiffType { ADDED, REMOVED, CONTEXT, SEPARATOR }

    private record DiffLine(DiffType type, String content) {}
}
