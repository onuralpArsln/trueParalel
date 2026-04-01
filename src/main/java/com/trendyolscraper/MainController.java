package com.trendyolscraper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        // Set up the Thread Pool
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfWorkers);

        // Submit the tasks
        for (int i = 1; i <= numberOfWorkers; i++) {
            ScraperDriver driver = new ScraperDriver(i, mode, gpuEnabled, keywords);
            executorService.submit(driver);
        }

        // Setup emergency stop background thread
        Thread emergencyStopListener = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n*** Press 'q' and Enter at any time to trigger an EMERGENCY STOP ***\n");
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("q".equalsIgnoreCase(input.trim())) {
                    System.out.println("\n[EMERGENCY STOP] 'q' detected. Forcefully shutting down JVM to terminate all Playwright processes immediately...");
                    System.exit(1); // Hard kill to ensure Playwright browsers die immediately
                }
            }
        });
        emergencyStopListener.setDaemon(true); // Ensures this thread doesn't prevent JVM exit
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

        System.out.println("Controller finished.");
        System.exit(0); // Ensure Playwright processes are completely released
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