package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced web scraper for Alaska Legislature
 * Author: [Your Name]
 * Date: November 5, 2025
 */
public class SimpleScraper {
    
    private static final String URL = "https://akleg.gov/senate.php";
    private static final String OUTPUT_FILE = "senators.json";
    
    private List<Senator> senators;
    
    public SimpleScraper() {
        senators = new ArrayList<>();
    }
    
    /**
     * Fetch and parse the webpage
     */
    public void scrape() {
        try {
            System.out.println("Connecting to: " + URL);
            
            // Fetch the webpage
            Document doc = Jsoup.connect(URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            
            System.out.println("Page loaded successfully!");
            System.out.println("Page title: " + doc.title());
            
            // Select senator list items
            Elements senatorElements = doc.select("ul.people-list > li");
            
            if (senatorElements.isEmpty()) {
                System.out.println("❌ No senator elements found!");
                System.out.println("\nTrying alternative selectors...\n");
                analyzeDocument(doc);
                return;
            }
            
            System.out.println("✓ Found " + senatorElements.size() + " senator elements\n");
            
            // Parse each senator
            int count = 0;
            for (Element element : senatorElements) {
                try {
                    Senator senator = parseElement(element);
                    if (senator.getName() != null && !senator.getName().trim().isEmpty()) {
                        senators.add(senator);
                        count++;
                        System.out.println(String.format("✓ [%d] %s - %s - %s", 
                            count, senator.getName(), senator.getParty(), senator.getPosition()));
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing element: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("\nTotal senators scraped: " + count);
            
        } catch (IOException e) {
            System.err.println("Error connecting to website: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Parse individual senator element
     */
    private Senator parseElement(Element element) {
        Senator senator = new Senator();
        
        // Extract the main link and name
        Element nameLink = element.selectFirst("a");
        if (nameLink != null) {
            String fullName = nameLink.text().trim();
            senator.setName(fullName);
            senator.setUrl(nameLink.attr("abs:href"));
        }
        
        // Extract image information (for name fallback)
        Element img = element.selectFirst("div.img-holder img");
        if (img != null) {
            String imgAlt = img.attr("alt");
            
            // Fallback name from alt text if needed
            if ((senator.getName() == null || senator.getName().isEmpty()) && !imgAlt.isEmpty()) {
                senator.setName(imgAlt);
            }
        }
        
        // Set title
        senator.setTitle("Senator");
        
        // Extract description information (party, district, location, etc.)
        Element descHolder = element.selectFirst("div.description-holder");
        if (descHolder != null) {
            String descText = descHolder.text().trim();
            
            // Parse party
            if (descText.contains("(R)") || descText.toLowerCase().contains("republican")) {
                senator.setParty("Republican");
            } else if (descText.contains("(D)") || descText.toLowerCase().contains("democrat")) {
                senator.setParty("Democrat");
            } else if (descText.contains("(I)") || descText.toLowerCase().contains("independent")) {
                senator.setParty("Independent");
            }
            
            // Parse position (district info) - look for patterns like "District A", "District 1", etc.
            if (descText.matches(".*District\\s+[A-Z0-9]+.*")) {
                String district = descText.replaceAll(".*District\\s+([A-Z0-9]+).*", "$1");
                senator.setPosition("District " + district);
            }
            
            // Parse address/location from description
            // Typically format is: "Party, District X, City, State"
            String[] parts = descText.split(",");
            if (parts.length >= 2) {
                // Try to build address from city and state if present
                StringBuilder addressBuilder = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].trim();
                    // Skip party indicators and district labels
                    if (!part.matches(".*District.*") && 
                        !part.equals("(R)") && !part.equals("(D)") && !part.equals("(I)")) {
                        if (addressBuilder.length() > 0) {
                            addressBuilder.append(", ");
                        }
                        addressBuilder.append(part);
                    }
                }
                if (addressBuilder.length() > 0) {
                    senator.setAddress(addressBuilder.toString());
                }
            }
        }
        
        // Extract email if present
        Element emailLink = element.selectFirst("a[href^=mailto:]");
        if (emailLink != null) {
            String email = emailLink.attr("href").replace("mailto:", "");
            senator.setEmail(email);
        }
        
        // Extract phone if present
        Element telLink = element.selectFirst("a[href^=tel:]");
        if (telLink != null) {
            String phone = telLink.attr("href").replace("tel:", "").trim();
            senator.setPhone(phone);
        }
        
        return senator;
    }
    
    /**
     * Analyze document structure (for debugging)
     */
    private void analyzeDocument(Document doc) {
        System.out.println("=== DOCUMENT ANALYSIS ===\n");
        
        System.out.println("Checking common selectors:");
        String[] selectors = {
            "ul.people-list > li",
            "ul.people-holder > li",
            "li.same-height-left",
            "div.legislator",
            "table tr"
        };
        
        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                System.out.println("✓ " + selector + " : " + elements.size() + " elements");
            } else {
                System.out.println("✗ " + selector + " : 0 elements");
            }
        }
        
        System.out.println("\nPage structure:");
        System.out.println("Total divs: " + doc.select("div").size());
        System.out.println("Total links: " + doc.select("a").size());
        System.out.println("Total images: " + doc.select("img").size());
    }
    
    /**
     * Save to JSON
     */
    public void saveToJson() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(senators);
            
            FileWriter writer = new FileWriter(OUTPUT_FILE);
            writer.write(json);
            writer.close();
            
            System.out.println("\n✓ Saved to: " + OUTPUT_FILE);
            System.out.println("✓ Total records: " + senators.size());
            
            // Print first senator as sample
            if (!senators.isEmpty()) {
                System.out.println("\n📋 Sample Record:");
                System.out.println(senators.get(0).toString());
            }
            
        } catch (IOException e) {
            System.err.println("Error saving JSON: " + e.getMessage());
        }
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║   Alaska Legislature Scraper v2.0         ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        SimpleScraper scraper = new SimpleScraper();
        scraper.scrape();
        
        if (!scraper.senators.isEmpty()) {
            scraper.saveToJson();
        }
        
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("\nTotal time: " + duration + " seconds");
    }
}