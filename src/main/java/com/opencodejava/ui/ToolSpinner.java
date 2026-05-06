package com.opencodejava.ui;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Displays a spinner with elapsed time during tool execution.
 */
public class ToolSpinner {
    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread spinnerThread;
    private long startTime;
    private String toolName;

    public void start(String toolName) {
        this.toolName = toolName;
        this.startTime = System.currentTimeMillis();
        this.running.set(true);

        spinnerThread = new Thread(() -> {
            int frameIndex = 0;
            while (running.get()) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                String display = String.format("\r  %s Running %s... %ds elapsed",
                        FRAMES[frameIndex % FRAMES.length], toolName, elapsed);
                System.out.print(display);
                System.out.flush();
                frameIndex++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "tool-spinner");
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    public void stop() {
        running.set(false);
        if (spinnerThread != null) {
            try {
                spinnerThread.join(500);
            } catch (InterruptedException ignored) {}
        }
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        System.out.print("\r" + " ".repeat(60) + "\r");
        if (elapsed > 1) {
            System.out.println("  ✓ " + toolName + " completed in " + elapsed + "s");
        }
        System.out.flush();
    }

    public boolean isRunning() {
        return running.get();
    }
}
