package ca.fusion3.tenderready.tenders;

import ca.fusion3.tenderready.common.AppProperties;
import ca.fusion3.tenderready.common.Hashing;
import ca.fusion3.tenderready.tenders.CanadaBuysCsvParser.ParseResult;
import ca.fusion3.tenderready.tenders.CanadaBuysCsvParser.ParsedNotice;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports the official CanadaBuys open tender notices feed. Notices are deduplicated by reference number.
 * A failed import changes nothing; a notice missing from a later import is kept (not treated as cancelled).
 */
@Service
public class TenderImportService {
    private static final Logger log = LoggerFactory.getLogger(TenderImportService.class);

    private final AppProperties props;
    private final TenderRepository tenders;
    private final ImportRunRepository runs;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();

    public TenderImportService(AppProperties props, TenderRepository tenders, ImportRunRepository runs,
                               TransactionTemplate tx) {
        this.props = props;
        this.tenders = tenders;
        this.runs = runs;
        this.tx = tx;
    }

    /** A run still marked RUNNING at startup was interrupted by a restart. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void closeInterruptedRuns() {
        runs.findAll().stream().filter(r -> "RUNNING".equals(r.getStatus())).forEach(r -> {
            r.setStatus("FAILED");
            r.setError("Interrupted by a backend restart. Previously imported notices are unchanged.");
            r.setFinishedAt(Instant.now());
            runs.save(r);
        });
    }

    public ImportRun importFromFeed(String trigger) {
        if (!lock.tryLock()) {
            throw ca.fusion3.tenderready.common.ApiException.conflict("IMPORT_RUNNING", "An import is already running.");
        }
        try {
            ImportRun run = runs.save(new ImportRun(Tender.SOURCE_CANADABUYS, props.feed().url(), trigger));
            try {
                ParseResult parsed = download(props.feed().url());
                tx.executeWithoutResult(s -> upsert(parsed, run));
                run.setStatus("SUCCEEDED");
            } catch (Exception e) {
                log.warn("CanadaBuys import failed: {}", e.toString());
                run.setStatus("FAILED");
                run.setError(describe(e));
            }
            run.setFinishedAt(Instant.now());
            return runs.save(run);
        } finally {
            lock.unlock();
        }
    }

    /** Package-visible so tests can import a fixture without a network call. */
    ImportRun importFrom(Reader reader, String trigger) throws IOException {
        ImportRun run = runs.save(new ImportRun(Tender.SOURCE_CANADABUYS, "test", trigger));
        ParseResult parsed = new CanadaBuysCsvParser().parse(reader);
        tx.executeWithoutResult(s -> upsert(parsed, run));
        run.setStatus("SUCCEEDED");
        run.setFinishedAt(Instant.now());
        return runs.save(run);
    }

    private ParseResult download(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(props.feed().timeoutSeconds()))
                .header("User-Agent", "TenderReady-hackathon-prototype/0.1")
                .GET().build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            resp.body().close();
            throw new IOException("Feed returned HTTP " + resp.statusCode());
        }
        // The request timeout only covers the response headers; this deadline also bounds the body download.
        java.util.concurrent.atomic.AtomicBoolean expired = new java.util.concurrent.atomic.AtomicBoolean();
        InputStream raw = resp.body();
        var watchdog = java.util.concurrent.CompletableFuture.runAsync(() -> {
            expired.set(true);
            try { raw.close(); } catch (IOException ignored) { }
        }, java.util.concurrent.CompletableFuture.delayedExecutor(props.feed().timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS));
        try (InputStream body = new BoundedInputStream(raw, props.feed().maxBytes());
             Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return new CanadaBuysCsvParser().parse(reader);
        } catch (IOException | RuntimeException e) {
            if (expired.get()) {
                throw new java.net.http.HttpTimeoutException("Feed download exceeded " + props.feed().timeoutSeconds() + " s");
            }
            throw e;
        } finally {
            watchdog.cancel(false);
        }
    }

    private void upsert(ParseResult parsed, ImportRun run) {
        Instant now = Instant.now();
        int inserted = 0, updated = 0, unchanged = 0;
        for (ParsedNotice n : parsed.notices()) {
            String hash = contentHash(n);
            Tender t = tenders.findBySourceAndSourceId(Tender.SOURCE_CANADABUYS, n.referenceNumber()).orElse(null);
            if (t == null) {
                t = new Tender(UUID.randomUUID(), Tender.SOURCE_CANADABUYS, n.referenceNumber());
                apply(t, n, hash, now);
                tenders.save(t);
                inserted++;
            } else if (!Objects.equals(t.getContentHash(), hash)) {
                apply(t, n, hash, now);
                updated++;
            } else {
                t.setLastSeenAt(now);
                unchanged++;
            }
        }
        run.setRowsRead(parsed.rowsRead());
        run.setSkipped(parsed.skipped());
        run.setInserted(inserted);
        run.setUpdated(updated);
        run.setUnchanged(unchanged);
    }

    private static void apply(Tender t, ParsedNotice n, String hash, Instant now) {
        t.setAmendmentNumber(n.amendmentNumber());
        t.setSolicitationNumber(n.solicitationNumber());
        t.setTitle(n.title());
        t.setBuyer(n.buyer());
        t.setBuyerAddress(n.buyerAddress());
        t.setDescription(n.description());
        t.setCategory(n.category());
        t.setSourceUrl(n.noticeUrl());
        t.setDeliveryRegionsRaw(n.deliveryRegionsRaw());
        t.setDeliveryAreas(n.deliveryAreas());
        t.setAttachments(n.attachments());
        t.setPublishedOn(n.publishedOn());
        t.setClosingAt(n.closingAt());
        t.setSourceTimezone(CanadaBuysCsvParser.SOURCE_TIMEZONE_LABEL);
        t.setStatus(n.status());
        t.setFetchedAt(now);
        t.setLastSeenAt(now);
        t.setContentHash(hash);
        t.setDemo(false);
    }

    static String contentHash(ParsedNotice n) {
        return Hashing.sha256(n.amendmentNumber(), n.title(), n.buyer(), n.buyerAddress(), n.description(),
                n.category(), n.noticeUrl(), n.deliveryRegionsRaw(), String.valueOf(n.attachments()),
                String.valueOf(n.publishedOn()), String.valueOf(n.closingAt()), n.status());
    }

    private static String describe(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "The CanadaBuys feed timed out. Previously imported notices are unchanged.";
        if (e instanceof java.net.ConnectException) return "Could not connect to the CanadaBuys feed. Previously imported notices are unchanged.";
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg + " — previously imported notices are unchanged.";
    }
}
