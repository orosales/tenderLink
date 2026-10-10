package ca.fusion3.tenderready.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tenderready")
public record AppProperties(
        boolean demoMode,
        String corsOrigins,
        int demoTenderClosingDays,
        Ai ai,
        Feed feed,
        Documents documents) {

    public record Ai(String mode, String model, String apiKey, int timeoutSeconds, int maxDocumentChars) {
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Feed(String url, boolean scheduleEnabled, String scheduleCron, int timeoutSeconds, long maxBytes) {}

    public record Documents(int timeoutSeconds, long maxBytes, String reviewedUrls) {}
}
