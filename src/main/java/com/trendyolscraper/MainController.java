package com.trendyolscraper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.microsoft.playwright.*;

public class MainController {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java MainController <numberOfWorkers> <mode> <gpuEnabled>");
            System.err.println("Example: java MainController 5 headless true");
            System.exit(1);
        }

        int numberOfWorkers = 1;
        try {
            numberOfWorkers = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number of workers provided. Using 1 as default.");
        }

        String mode = args[1]; // headless or headed
        boolean gpuEnabled = Boolean.parseBoolean(args[2]);

        System.out.println("Starting Controller with:");
        System.out.println(" - Workers: " + numberOfWorkers);
        System.out.println(" - Mode: " + mode);
        System.out.println(" - GPU Enabled: " + gpuEnabled);

        List<String> keywords = loadKeywords();
        if (keywords.isEmpty()) {
            System.out.println("Warning: keywords.txt is empty or missing. Using default keywords.");
            keywords.add("bilgisayar");
        }

        // Set up Playwright and Browser once
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(mode.equalsIgnoreCase("headless"));

            // Use stable flags for macOS
            List<String> argsList = new ArrayList<>();
            argsList.add("--ignore-certificate-errors");

            if (!gpuEnabled) {
                argsList.add("--disable-gpu");
            }
            launchOptions.setArgs(argsList);

            System.out.println("Launching Chromium...");
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                
                // Final stability check - wait for browser connection to settle
                Thread.sleep(2000); 

                // Set up the Thread Pool
                ExecutorService executorService = Executors.newFixedThreadPool(numberOfWorkers);

                // Submit the tasks
                for (int i = 1; i <= numberOfWorkers; i++) {
                    ScraperDriver driver = new ScraperDriver(i, mode, gpuEnabled, keywords, browser);
                    executorService.submit(driver);
                }

                // Setup emergency stop background thread
                Thread emergencyStopListener = new Thread(() -> {
                    Scanner scanner = new Scanner(System.in);
                    System.out.println("\n*** Press 'q' and Enter at any time to trigger an EMERGENCY STOP ***\n");
                    while (scanner.hasNextLine()) {
                        String input = scanner.nextLine();
                        if ("q".equalsIgnoreCase(input.trim())) {
                            System.out.println("\n[EMERGENCY STOP] 'q' detected. Forcefully shutting down JVM...");
                            System.exit(1);
                        }
                    }
                });
                emergencyStopListener.setDaemon(true);
                emergencyStopListener.start();

                // Shut down the executor gracefully and wait for completion
                executorService.shutdown();
                try {
                    // Wait up to 5 minutes for completion
                    if (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
                        System.out.println("Warning: Timeout reached. Forcing shutdown...");
                        executorService.shutdownNow();
                    } else {
                        System.out.println("All tasks completed successfully.");
                    }
                } catch (InterruptedException e) {
                    System.out.println("Main thread interrupted. Forcing shutdown...");
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            // Check for the known Playwright Java shutdown bug (NegativeArraySizeException)
            if (!(e instanceof java.lang.NegativeArraySizeException)) {
                System.err.println("Error in Playwright session: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Controller finished.");
        System.exit(0);
    }

    private static List<String> loadKeywords() {
        List<String> keywords = new ArrayList<>();
        Path path = Paths.get("keywords.txt");
        try {
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        keywords.add(line.trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read keywords.txt: " + e.getMessage());
        }
        return keywords;
    }
}