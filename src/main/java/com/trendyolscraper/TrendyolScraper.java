package com.trendyolscraper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrendyolScraper {

    private final Browser browser;

    public TrendyolScraper(String mode, boolean gpuEnabled, Browser browser) {
        this.browser = browser;
    }

    public List<String> searchAndExtract(String keyword, int x, int y, int z, int workerId) {
        List<String> results = new ArrayList<>();

        // Create a new context and page from the shared browser
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080))) {
            
            // RADICAL MODAL KILLER: Injects a script to remove overlays and modals globally via CSS
            context.addInitScript("() => {\n" +
                "  const style = document.createElement('style');\n" +
                "  style.innerHTML = `\n" +
                "    .modal-container, .modal-close, #modals, \n" +
                "    #onetrust-banner-sdk, .popup-container, .overlay, .overlay-container {\n" +
                "      display: none !important; \n" +
                "      visibility: hidden !important; \n" +
                "      pointer-events: none !important;\n" +
                "    }`;\n" +
                "  document.head.appendChild(style);\n" +
                "  \n" +
                "  // Remove them from DOM every second just in case they are re-added\n" +
                "  setInterval(() => {\n" +
                "    ['.modal-container', '#modals', '#onetrust-banner-sdk'].forEach(sel => {\n" +
                "       document.querySelectorAll(sel).forEach(el => el.remove());\n" +
                "    });\n" +
                "  }, 1000);\n" +
                "}");

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

            // Click the search placeholder (if it exists)
            Locator searchPlaceholder = page.locator(".suggestion-placeholder, button[data-testid='suggestion-placeholder']");
            if (searchPlaceholder.isVisible()) {
                try {
                    searchPlaceholder.click(new Locator.ClickOptions().setForce(true).setTimeout(3000));
                } catch (Exception e) {}
                page.waitForTimeout(500);
            }

            // Fill actual search bar
            Locator searchInput = page.locator("input[data-testid='suggestion'], input.search-input");
            
            // Try to interact naturally first
            try {
                searchInput.click(new Locator.ClickOptions().setForce(true));
                // Clear any existing value just in case
                page.keyboard().press("Control+A");
                page.keyboard().press("Backspace");
                // Type with a small delay to simulate a human
                searchInput.type(keyword, new Locator.TypeOptions().setDelay(50));
            } catch (Exception e) {
                // Fallback to direct focus and type
                page.evaluate("selector => document.querySelector(selector)?.focus()", "input[data-testid='suggestion']");
                page.keyboard().type(keyword);
            }

            // Press enter and wait for navigation or a change in URL
            searchInput.press("Enter");
            
            try {
                // Wait for search result page to load (URL changes to include /sr?q=)
                page.waitForURL("**/sr?q=**", new Page.WaitForURLOptions().setTimeout(10000));
            } catch (Exception e) {
                // If URL redirect didn't happen, maybe it's an AJAX load
                page.waitForTimeout(2000);
            }

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