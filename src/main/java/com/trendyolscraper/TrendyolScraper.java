package com.trendyolscraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrendyolScraper {

    private final String mode;
    private final boolean gpuEnabled;

    public TrendyolScraper(String mode, boolean gpuEnabled) {
        this.mode = mode;
        this.gpuEnabled = gpuEnabled;
    }

    public List<String> searchAndExtract(String keyword, int x, int y, int z, int workerId) {
        List<String> results = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(mode.equalsIgnoreCase("headless"));

            if (gpuEnabled) {
                // To use GPU in headless Chromium, we often need to disable software rasterizer
                // and pass flags to force hardware acceleration.
                // If headed, it uses GPU by default if available.
                launchOptions.setArgs(Arrays.asList(
                        "--ignore-certificate-errors",
                        "--use-gl=egl",
                        "--enable-unsafe-webgpu",
                        "--enable-features=Vulkan"
                ));
            } else {
                 launchOptions.setArgs(Arrays.asList(
                        "--disable-gpu"
                 ));
            }

            // Create browser instance
            // We use Chromium as it gives the best standard behavior
            Browser browser = playwright.chromium().launch(launchOptions);

            // ==========================================
            // PLACEHOLDER FOR PROXY AND FINGERPRINTING
            // ==========================================
            // To add a proxy:
            // Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
            //      .setProxy(new Proxy("http://your-proxy-ip:port").setUsername("usr").setPassword("pwd"))
            //      .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...")
            //      .setViewportSize(1920, 1080);
            // BrowserContext context = browser.newContext(contextOptions);
            // ==========================================

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080));

            Page page = context.newPage();

            // Set a generous timeout
            page.setDefaultTimeout(30000);

            // Navigate to trendyol
            page.navigate("https://www.trendyol.com/");

            // Wait a moment for initial load and popups
            page.waitForTimeout(2000);

            // Handle Popups
            // 1. Gender / Country Selection
            try {
                Locator closeGenderPopup = page.locator(".modal-close").first();
                if (closeGenderPopup.count() > 0 && closeGenderPopup.isVisible()) {
                    closeGenderPopup.click();
                    page.waitForTimeout(500);
                }
            } catch (Exception e) {
                // Ignore if not present
            }

            // 2. Cookie Consent
            try {
                 Locator acceptCookies = page.locator("#onetrust-accept-btn-handler");
                 if (acceptCookies.count() > 0 && acceptCookies.isVisible()) {
                     acceptCookies.click();
                     page.waitForTimeout(500);
                 }
            } catch (Exception e) {
                 // Ignore if not present
            }

            // Find search bar, type the keyword and press Enter
            Locator searchInput = page.locator("input[data-testid='suggestion']");
            if (searchInput.count() == 0) {
                 // Try alternative selector if they change classes
                 searchInput = page.locator(".vQI670rJ"); // trendyol search input container
            }

            searchInput.fill(keyword);
            searchInput.press("Enter");

            // Wait for results to load by waiting for product containers
            try {
                page.waitForSelector(".prdct-cntnr-wrppr", new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception e) {
                results.add("Error: Could not find product container after search for " + keyword);
                context.close();
                return results;
            }

            // Scroll down a bit to ensure lazy-loaded items are fetched
            for (int i = 0; i < 5; i++) {
                page.mouse().wheel(0, 1000);
                page.waitForTimeout(1000); // give it time to fetch images/products
            }

            // Find all product names
            // Product names are typically inside spans with class "prdct-desc-cntnr-name"
            Locator products = page.locator(".prdct-desc-cntnr-name");

            // Wait until at least the first product loads and is visible
            if (products.count() > 0) {
                 products.first().waitFor(new Locator.WaitForOptions().setTimeout(5000));
            }

            int maxRequiredIndex = Math.max(Math.max(x, y), z) - 1;
            int count = products.count();

            // Ensure we have enough products
            if (count > maxRequiredIndex) {
                results.add(x + "th product: " + products.nth(x - 1).textContent());
                results.add(y + "th product: " + products.nth(y - 1).textContent());
                results.add(z + "th product: " + products.nth(z - 1).textContent());
            } else {
                results.add("Error: Not enough products found. Needed up to " + (maxRequiredIndex + 1) + " but found " + count);
            }

            context.close();
        } catch (Exception e) {
            results.add("Error during scraping: " + e.getMessage());
        }

        return results;
    }
}