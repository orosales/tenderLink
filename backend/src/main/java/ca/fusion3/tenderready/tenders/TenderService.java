package ca.fusion3.tenderready.tenders;

import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.common.AppProperties;
import ca.fusion3.tenderready.common.Hashing;
import ca.fusion3.tenderready.common.Text;
import ca.fusion3.tenderready.tenders.TenderDtos.DocumentView;
import ca.fusion3.tenderready.tenders.TenderDtos.ImportRunView;
import ca.fusion3.tenderready.tenders.TenderDtos.TenderDetail;
import ca.fusion3.tenderready.tenders.TenderDtos.TenderList;
import ca.fusion3.tenderready.tenders.TenderDtos.TenderSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenderService {
    private static final int MAX_DOCUMENTS = 4;

    private final TenderRepository tenders;
    private final TenderDocumentRepository documents;
    private final ImportRunRepository runs;
    private final SafeDocumentFetcher fetcher;
    private final DocumentTextExtractor extractor;
    private final AppProperties props;

    public TenderService(TenderRepository tenders, TenderDocumentRepository documents, ImportRunRepository runs,
                         SafeDocumentFetcher fetcher, DocumentTextExtractor extractor, AppProperties props) {
        this.tenders = tenders;
        this.documents = documents;
        this.runs = runs;
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.props = props;
    }

    public Tender get(UUID id) {
        return tenders.findById(id).orElseThrow(() -> ApiException.notFound("Tender"));
    }

    @Transactional(readOnly = true)
    public TenderList list(String service, String location, boolean includeUnknownLocation, int limit) {
        String q = Text.isBlank(service) ? "" : "%" + service.trim().toLowerCase(Locale.ROOT) + "%";
        String loc = Text.isBlank(location) ? "" : "%" + location.trim().toLowerCase(Locale.ROOT) + "%";
        List<TenderSummary> demo = tenders.findByDemoTrue().stream()
                .filter(t -> matchesDemo(t, q, loc, includeUnknownLocation))
                .map(TenderSummary::of).toList();
        List<TenderSummary> live = tenders.searchLive(q, loc, includeUnknownLocation, PageRequest.of(0, Math.min(limit, 300)))
                .stream().map(TenderSummary::of).toList();
        return new TenderList(demo, live, tenders.countLive(q, loc, includeUnknownLocation),
                tenders.countBySource(Tender.SOURCE_CANADABUYS),
                runs.findFirstByOrderByStartedAtDesc().map(ImportRunView::of).orElse(null),
                runs.findFirstByStatusOrderByStartedAtDesc("SUCCEEDED").map(ImportRunView::of).orElse(null),
                TenderDtos.CANADABUYS_LABEL);
    }

    private static boolean matchesDemo(Tender t, String q, String loc, boolean includeUnknown) {
        String needleQ = q.replace("%", "");
        String needleLoc = loc.replace("%", "");
        boolean qOk = needleQ.isEmpty() || (t.getTitle() + " " + t.getDescription() + " " + t.getCategory())
                .toLowerCase(Locale.ROOT).contains(needleQ);
        boolean locOk = needleLoc.isEmpty()
                || (t.getDeliveryAreas() == null ? includeUnknown
                : String.join(" ", t.getDeliveryAreas()).toLowerCase(Locale.ROOT).contains(needleLoc)
                  || "nova scotia".contains(needleLoc));
        return qOk && locOk;
    }

    @Transactional(readOnly = true)
    public TenderDetail detail(UUID id) {
        Tender t = get(id);
        List<TenderDocument> docs = documents.findByTenderIdOrderByNameAsc(id);
        DocumentBundle bundle = bundle(t, docs);
        String state;
        String message;
        if (bundle.complete()) {
            state = "COMPLETE";
            message = t.isDemo() ? "Bundled sample solicitation document." : "All listed documents were retrieved and extracted.";
        } else if (docs.stream().anyMatch(d -> d.getAccessStatus() == TenderDocument.AccessStatus.AVAILABLE)) {
            state = "PARTIAL";
            message = "Document unavailable / assessment incomplete: some documents could not be retrieved.";
        } else if (docs.isEmpty()) {
            state = "NOT_RETRIEVED";
            message = candidateUrls(t).isEmpty()
                    ? "Document unavailable / assessment incomplete: the notice lists no public attachment. Only the notice text can be analysed."
                    : "Documents have not been retrieved yet.";
        } else {
            state = "UNAVAILABLE";
            message = "Document unavailable / assessment incomplete: no listed document could be retrieved.";
        }
        return new TenderDetail(TenderSummary.of(t), t.getSolicitationNumber(), t.getAmendmentNumber(),
                t.getDescription(), t.getSourceUrl(), t.getDeliveryRegionsRaw(), t.getAttachments(), t.getPublishedOn(),
                docs.stream().map(DocumentView::of).toList(), state, message);
    }

    /** Attempts to retrieve each public attachment listed by the notice (plus a configured reviewed URL). */
    @Transactional
    public TenderDetail fetchDocuments(UUID id) {
        Tender t = get(id);
        if (t.isDemo()) {
            throw ApiException.unprocessable("DEMO_DOCUMENT", "The sample scenario uses its bundled document.");
        }
        List<String> urls = candidateUrls(t);
        documents.deleteByTenderId(id);
        documents.flush();
        for (String url : urls.stream().limit(MAX_DOCUMENTS).toList()) {
            TenderDocument d = new TenderDocument(UUID.randomUUID(), id, nameFor(url), url);
            try {
                SafeDocumentFetcher.Fetched f = fetcher.fetch(url);
                DocumentTextExtractor.Extracted e = extractor.extract(f);
                d.setAccessStatus(e.status());
                d.setAccessNote(e.note());
                if (e.text() != null) {
                    d.setExtractedText(e.text());
                    d.setVersionHash(Hashing.sha256Text(e.text()));
                }
            } catch (SafeDocumentFetcher.BlockedUrlException e) {
                d.setAccessStatus(TenderDocument.AccessStatus.UNAVAILABLE);
                d.setAccessNote("Blocked: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                d.setAccessStatus(TenderDocument.AccessStatus.UNAVAILABLE);
                d.setAccessNote("Interrupted");
            } catch (Exception e) {
                d.setAccessStatus(TenderDocument.AccessStatus.UNAVAILABLE);
                d.setAccessNote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            d.setFetchedAt(Instant.now());
            documents.save(d);
        }
        if (urls.size() > MAX_DOCUMENTS) {
            TenderDocument d = new TenderDocument(UUID.randomUUID(), id, "Additional documents", null);
            d.setAccessStatus(TenderDocument.AccessStatus.UNAVAILABLE);
            d.setAccessNote((urls.size() - MAX_DOCUMENTS) + " more attachment(s) were not retrieved by this prototype.");
            documents.save(d);
        }
        return detail(id);
    }

    @Transactional(readOnly = true)
    public DocumentBundle bundle(UUID tenderId) {
        Tender t = get(tenderId);
        return bundle(t, documents.findByTenderIdOrderByNameAsc(tenderId));
    }

    private DocumentBundle bundle(Tender t, List<TenderDocument> docs) {
        List<TenderDocument> available = docs.stream()
                .filter(d -> d.getAccessStatus() == TenderDocument.AccessStatus.AVAILABLE && d.getExtractedText() != null)
                .sorted(Comparator.comparing(TenderDocument::getName))
                .toList();
        List<String> limitations = new ArrayList<>();
        docs.stream().filter(d -> d.getAccessStatus() != TenderDocument.AccessStatus.AVAILABLE)
                .forEach(d -> limitations.add(d.getName() + ": " + d.getAccessNote()));
        if (available.isEmpty()) {
            String notice = "NOTICE TITLE: " + t.getTitle() + "\n\nNOTICE DESCRIPTION:\n"
                    + (t.getDescription() == null ? "(none)" : t.getDescription());
            limitations.add(0, "Document unavailable / assessment incomplete: only the short public notice text was analysed.");
            return new DocumentBundle(notice, Hashing.sha256("notice", t.getContentHash()), false, null,
                    List.of("Public notice text"), limitations);
        }
        StringBuilder sb = new StringBuilder();
        for (TenderDocument d : available) {
            sb.append("=== Document: ").append(d.getName()).append(" ===\n").append(d.getExtractedText()).append("\n\n");
        }
        String text = sb.toString();
        boolean complete = t.isDemo() || (limitations.isEmpty() && !docs.isEmpty());
        String version = available.size() == 1 ? available.get(0).getVersionHash() : Hashing.sha256Text(text);
        return new DocumentBundle(text, version, complete, available.get(0).getId(),
                available.stream().map(TenderDocument::getName).toList(), limitations);
    }

    private List<String> candidateUrls(Tender t) {
        Set<String> urls = new LinkedHashSet<>();
        String reviewed = reviewedUrls().get(t.getSourceId());
        if (reviewed != null) urls.add(reviewed);
        if (t.getAttachments() != null) urls.addAll(t.getAttachments());
        return new ArrayList<>(urls);
    }

    private Map<String, String> reviewedUrls() {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = props.documents().reviewedUrls();
        if (Text.isBlank(raw)) return map;
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return map;
    }

    private static String nameFor(String url) {
        String path = url.replaceAll("[?#].*$", "");
        String last = path.substring(path.lastIndexOf('/') + 1);
        return Text.isBlank(last) ? url : Text.truncate(last, 120);
    }
}
