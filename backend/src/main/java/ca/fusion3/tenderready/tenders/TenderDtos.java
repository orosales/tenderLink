package ca.fusion3.tenderready.tenders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TenderDtos {
    private TenderDtos() {}

    public static final String CANADABUYS_LABEL = "CanadaBuys: federal opportunities";
    public static final String DEMO_LABEL = "Sample scenario";

    public record TenderSummary(
            UUID id, String source, String sourceLabel, String sourceId, String title, String buyer,
            String buyerAddress, List<String> deliveryAreas, boolean deliveryLocationUnknown, String category,
            Instant closingAt, String sourceTimezone, boolean closed, String status, Instant fetchedAt, boolean demo) {

        public static TenderSummary of(Tender t) {
            return new TenderSummary(t.getId(), t.getSource(), t.isDemo() ? DEMO_LABEL : CANADABUYS_LABEL,
                    t.getSourceId(), t.getTitle(), t.getBuyer(), t.getBuyerAddress(), t.getDeliveryAreas(),
                    t.getDeliveryAreas() == null || t.getDeliveryAreas().isEmpty(), t.getCategory(), t.getClosingAt(),
                    t.getSourceTimezone(), t.getClosingAt() != null && t.getClosingAt().isBefore(Instant.now()),
                    t.getStatus(), t.getFetchedAt(), t.isDemo());
        }
    }

    public record DocumentView(UUID id, String name, String url, String accessStatus, String accessNote,
                               String versionHash, int characters, Instant fetchedAt, boolean demo) {
        static DocumentView of(TenderDocument d) {
            return new DocumentView(d.getId(), d.getName(), d.getUrl(), d.getAccessStatus().name(), d.getAccessNote(),
                    d.getVersionHash(), d.getExtractedText() == null ? 0 : d.getExtractedText().length(),
                    d.getFetchedAt(), d.isDemo());
        }
    }

    public record TenderDetail(
            TenderSummary summary, String solicitationNumber, String amendmentNumber, String description,
            String sourceUrl, String deliveryRegionsRaw, List<String> attachments, LocalDate publishedOn,
            List<DocumentView> documents, String documentState, String documentStateMessage) {}

    public record ImportRunView(UUID id, String status, String trigger, String url, Instant startedAt,
                                Instant finishedAt, int rowsRead, int inserted, int updated, int unchanged,
                                int skipped, String error) {
        public static ImportRunView of(ImportRun r) {
            return new ImportRunView(r.getId(), r.getStatus(), r.getTrigger(), r.getUrl(), r.getStartedAt(),
                    r.getFinishedAt(), r.getRowsRead(), r.getInserted(), r.getUpdated(), r.getUnchanged(),
                    r.getSkipped(), r.getError());
        }
    }

    public record TenderList(List<TenderSummary> demo, List<TenderSummary> live, long liveMatching, long liveTotal,
                             ImportRunView lastImport, ImportRunView lastSuccessfulImport, String liveSourceLabel) {}
}
