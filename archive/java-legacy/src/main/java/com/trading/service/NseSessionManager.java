package com.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class NseSessionManager {
    
    private final RestTemplate restTemplate;
    private String sessionCookie;
    private long lastSessionTime;
    private static final long SESSION_TIMEOUT = 300000; // 5 minutes
    
    public NseSessionManager() {
        this.restTemplate = new RestTemplate();
        this.lastSessionTime = 0;
    }
    
    public String getValidSessionCookie() {
        long currentTime = System.currentTimeMillis();
        
        // Check if session is still valid
        if (sessionCookie != null && (currentTime - lastSessionTime) < SESSION_TIMEOUT) {
            return sessionCookie;
        }
        
        // Create new session
        try {
            return createNewSession();
        } catch (Exception e) {
            System.err.println("Failed to create NSE session: " + e.getMessage());
            return getFallbackCookie();
        }
    }
    
    private String createNewSession() throws Exception {
        // First, visit the NSE homepage to get initial cookies
        String homeUrl = "https://www.nseindia.com/";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
        headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Sec-Fetch-Dest", "document");
        headers.set("Sec-Fetch-Mode", "navigate");
        headers.set("Sec-Fetch-Site", "none");
        headers.set("Sec-Fetch-User", "?1");
        headers.set("Upgrade-Insecure-Requests", "1");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(homeUrl, HttpMethod.GET, entity, String.class);
        
        // Extract cookies from response
        String setCookieHeader = response.getHeaders().getFirst("Set-Cookie");
        if (setCookieHeader != null) {
            sessionCookie = setCookieHeader;
            lastSessionTime = System.currentTimeMillis();
            return sessionCookie;
        }
        
        throw new RuntimeException("Failed to get session cookies from NSE");
    }
    
    private String getFallbackCookie() {
        // Return a basic cookie structure for fallback
        return "_ga=GA1.1.845952303.1745206525; AKA_A2=A; nsit=E2Clsnz1gzMBTFEV4RhieuLT";
    }
    
    public HttpHeaders getNseHeaders(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        
        // Use the exact headers from your curl command
        headers.set("Accept", "*/*");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Priority", "u=1, i");
        headers.set("Referer", "https://www.nseindia.com/get-quotes/equity?symbol=" + symbol);
        headers.set("Sec-Ch-Ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"macOS\"");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
        
        // Set the session cookie
        String cookie = getValidSessionCookie();
        headers.set("Cookie", cookie);
        
        return headers;
    }
}
