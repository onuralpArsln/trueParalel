package com.trendyolscraper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.microsoft.playwright.Browser;

public class ScraperDriver implements Runnable {


    private final int workerId;
    private final String mode;
    private final boolean gpuEnabled;
    private final List<String> keywords;
    private final Random random;
    private final Browser browser;

    public ScraperDriver(int workerId, String mode, boolean gpuEnabled, List<String> keywords, Browser browser) {
        this.workerId = workerId;
        this.mode = mode;
        this.gpuEnabled = gpuEnabled;
        this.keywords = keywords;
        this.random = new Random();
        this.browser = browser;
    }

    @Override
    public void run() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        System.out.println("[Worker " + workerId + "] Starting...");

        // Small delay to prevent race conditions during context creation on macOS
        try {
            Thread.sleep(random.nextInt(1000)); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Pick a random keyword
        String keyword = keywords.isEmpty() ? "bilgisayar" : keywords.get(random.nextInt(keywords.size()));

        // Generate 3 distinct random numbers between 1 and 20
        List<Integer> randomNumbers = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            randomNumbers.add(i);
        }
        Collections.shuffle(randomNumbers, random);

        int x = randomNumbers.get(0);
        int y = randomNumbers.get(1);
        int z = randomNumbers.get(2);

        System.out.println("[Worker " + workerId + "] Keyword: '" + keyword + "', fetching items at rank: " + x + ", " + y + ", " + z);

        List<String> results;
        TrendyolScraper scraper = new TrendyolScraper(mode, gpuEnabled, browser);
        results = scraper.searchAndExtract(keyword, x, y, z, workerId);

        if (Thread.currentThread().isInterrupted()) {
            System.out.println("[Worker " + workerId + "] Interrupted after scraping.");
            return;
        }

        System.out.println("=========================================");
        System.out.println("[Worker " + workerId + "] Results for '" + keyword + "':");
        for (String result : results) {
            System.out.println("  -> " + result);
        }
        System.out.println("=========================================");
    }
}