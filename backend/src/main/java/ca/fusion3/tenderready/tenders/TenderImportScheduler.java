package ca.fusion3.tenderready.tenders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional daily import. Enabled with TENDER_IMPORT_SCHEDULE_ENABLED=true. */
@Component
@ConditionalOnProperty(name = "tenderready.feed.schedule-enabled", havingValue = "true")
public class TenderImportScheduler {
    private static final Logger log = LoggerFactory.getLogger(TenderImportScheduler.class);
    private final TenderImportService importer;

    public TenderImportScheduler(TenderImportService importer) {
        this.importer = importer;
    }

    @Scheduled(cron = "${tenderready.feed.schedule-cron}")
    public void run() {
        ImportRun run = importer.importFromFeed("SCHEDULE");
        log.info("Scheduled CanadaBuys import {}: {} inserted, {} updated", run.getStatus(), run.getInserted(), run.getUpdated());
    }
}
