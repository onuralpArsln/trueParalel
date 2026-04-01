package com.trendyolscraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.ArrayList;
import java.util.List;

public class TrendyolScraper {

    private final Browser browser;

    public TrendyolScraper(String mode, boolean gpuEnabled, Browser browser) {
        this.browser = browser;
    }

    public List<String> searchAndExtract(String keyword, int x, int y, int z, int workerId) {
        List<String> results = new ArrayList<>();

        // Create a new context and page from the shared browser
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080))) {
            
            Page page = context.newPage();

            // Set a generous timeout
            page.setDefaultTimeout(30000);

            // Navigate to trendyol
            page.navigate("https://www.trendyol.com/");

            // Wait for initial load and popups
            page.waitForTimeout(4000);

            // Handle Popups (Retry mechanism for stubborn modals)
            for (int i = 0; i < 2; i++) {
                // 1. Gender / Country Selection Modal
                try {
                    Locator genderModalClose = page.locator(".modal-close");
                    if (genderModalClose.isVisible()) {
                        genderModalClose.click();
                        page.waitForTimeout(1000);
                    } else {
                        // Sometimes the modal-close is hidden, but gender buttons are visible
                        Locator genderButtons = page.locator(".gender-button");
                        if (genderButtons.count() > 0 && genderButtons.first().isVisible()) {
                            genderButtons.first().click();
                            page.waitForTimeout(1000);
                        }
                    }
                } catch (Exception e) {}

                // 2. Cookie Consent Banner
                try {
                    Locator acceptCookies = page.locator("#onetrust-accept-btn-handler");
                    if (acceptCookies.isVisible()) {
                        acceptCookies.click();
                        page.waitForTimeout(500);
                    }
                } catch (Exception e) {}
                
                // 3. Campaign/Welcome popups
                try {
                    Locator campaignClose = page.locator(".campaign-button-close, .popup-close");
                    if (campaignClose.count() > 0 && campaignClose.first().isVisible()) {
                        campaignClose.first().click();
                        page.waitForTimeout(500);
                    }
                } catch (Exception e) {}
            }

            // Click the search placeholder (use force if needed as modals might still be fading)
            Locator searchPlaceholder = page.locator(".suggestion-placeholder");
            if (searchPlaceholder.isVisible()) {
                try {
                    searchPlaceholder.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));
                } catch (Exception e) {
                    // fall back to direct search input if placeholder click fails
                }
                page.waitForTimeout(500);
            }

            // Final check for actual search bar
            Locator searchInput = page.locator("input[data-testid='suggestion'], input.search-input");
            if (!searchInput.isVisible()) {
                // If it's still not visible, it might be that the placeholder click didn't work.
                // We'll try to focus it directly.
                try {
                    searchInput.focus(new Locator.FocusOptions().setTimeout(2000));
                } catch (Exception e) {}
            }

            if (!searchInput.isVisible()) {
                results.add("Error: Still could not see search input for " + keyword + " (possibly blocked by modal)");
                context.close();
                return results;
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
            e.printStackTrace();
        }

        return results;
    }
}