package ca.fusion3.tenderready.demo;

import ca.fusion3.tenderready.assessment.AssessmentRepository;
import ca.fusion3.tenderready.assessment.AssessmentService;
import ca.fusion3.tenderready.assessment.Model;
import ca.fusion3.tenderready.common.AppProperties;
import ca.fusion3.tenderready.common.Hashing;
import ca.fusion3.tenderready.companies.CapacityEntry;
import ca.fusion3.tenderready.companies.Company;
import ca.fusion3.tenderready.companies.CompanyRepository;
import ca.fusion3.tenderready.companies.Qualification;
import ca.fusion3.tenderready.companies.Qualification.EvidenceStatus;
import ca.fusion3.tenderready.tenders.Tender;
import ca.fusion3.tenderready.tenders.TenderDocument;
import ca.fusion3.tenderready.tenders.TenderDocumentRepository;
import ca.fusion3.tenderready.tenders.TenderRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the fictional demo companies and tender if they are missing. Never touches imported records. */
@Component
public class DemoSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final ZoneId ATLANTIC = ZoneId.of("America/Halifax");

    private final CompanyRepository companies;
    private final TenderRepository tenders;
    private final TenderDocumentRepository documents;
    private final AppProperties props;
    private final AssessmentService assessments;
    private final AssessmentRepository assessmentRepository;

    public DemoSeeder(CompanyRepository companies, TenderRepository tenders, TenderDocumentRepository documents,
                      AppProperties props, AssessmentService assessments, AssessmentRepository assessmentRepository) {
        this.companies = companies;
        this.tenders = tenders;
        this.documents = documents;
        this.props = props;
        this.assessments = assessments;
        this.assessmentRepository = assessmentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        seed();
    }

    @Transactional
    public void seed() throws IOException {
        if (!companies.existsById(DemoData.HARBOUR_CLEAN)) companies.save(harbourClean());
        if (!companies.existsById(DemoData.FUNDY_CLEAN)) companies.save(fundyClean());

        String text = readDocument();
        Tender t = tenders.findById(DemoData.DEMO_TENDER).orElse(null);
        if (t == null) {
            t = new Tender(DemoData.DEMO_TENDER, Tender.SOURCE_DEMO, DemoData.DEMO_TENDER_SOURCE_ID);
            t.setTitle("Cleaning services at federal offices in Halifax, Truro and Kentville");
            t.setBuyer("Atlantic Federal Facilities Office");
            t.setBuyerAddress("Dartmouth, Nova Scotia");
            t.setDescription("Commercial office cleaning at three offices: 60 weekly labour hours in Halifax, 20 in Truro "
                    + "and 15 in Kentville. Subcontracting permitted only with named subcontractors and buyer approval. "
                    + "See the bundled document for full requirements.");
            t.setCategory("Commercial office cleaning");
            t.setSolicitationNumber(DemoData.DEMO_TENDER_SOURCE_ID);
            t.setAmendmentNumber("000");
            t.setDeliveryAreas(new ArrayList<>(List.of("Halifax", "Truro", "Kentville")));
            t.setDeliveryRegionsRaw("Halifax, Nova Scotia; Truro, Nova Scotia; Kentville, Nova Scotia");
            t.setStatus("Open");
            t.setPublishedOn(LocalDate.now(ATLANTIC));
            t.setFetchedAt(Instant.now());
            t.setDemo(true);
        }
        if (t.getClosingAt() == null || t.getClosingAt().isBefore(Instant.now().plusSeconds(86400))) {
            t.setClosingAt(LocalDate.now(ATLANTIC).plusDays(props.demoTenderClosingDays()).atTime(LocalTime.of(14, 0))
                    .atZone(ATLANTIC).toInstant());
        }
        t.setSourceTimezone("America/Halifax (sample closing date, set relative to today)");
        t.setContentHash(Hashing.sha256(t.getTitle(), t.getDescription(), text));
        tenders.save(t);

        TenderDocument d = documents.findById(DemoData.DEMO_DOCUMENT)
                .orElseGet(() -> new TenderDocument(DemoData.DEMO_DOCUMENT, DemoData.DEMO_TENDER,
                        "RFP DEMO-NS-CLN-0426.txt", null));
        d.setExtractedText(text);
        d.setVersionHash(Hashing.sha256Text(text));
        d.setAccessStatus(TenderDocument.AccessStatus.AVAILABLE);
        d.setAccessNote("Bundled sample document (plain text, sections 1-6)");
        d.setDemo(true);
        documents.save(d);
        documents.flush();

        // Ready-made starting assessments (bundled demonstration analysis, deterministic checks on the seeded
        // profiles) so the sample scenario shows findings immediately. "Run gap assessment" re-runs them live.
        for (var id : List.of(DemoData.HARBOUR_CLEAN, DemoData.FUNDY_CLEAN)) {
            Company c = companies.findById(id).orElseThrow();
            boolean assessed = assessmentRepository
                    .findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(DemoData.DEMO_TENDER, id).isPresent();
            if (!assessed && c.isConfirmed()) {
                assessments.assess(c, DemoData.DEMO_TENDER, Model.Mode.FIXTURE);
            }
        }
        log.info("Demo data ready (fictional companies and tender)");
    }

    static String readDocument() throws IOException {
        try (InputStream in = new ClassPathResource("demo/demo-tender-document.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Company harbourClean() {
        Company c = new Company(DemoData.HARBOUR_CLEAN, "Harbour Clean");
        c.setContactName("Avery Lee");
        c.setServices(new ArrayList<>(List.of("Commercial office cleaning")));
        c.setSpecializations(new ArrayList<>(List.of("Office buildings", "Green cleaning products")));
        c.setServiceAreas(new ArrayList<>(List.of("Halifax")));
        c.setCapacity(new ArrayList<>(List.of(new CapacityEntry("Halifax", "Commercial office cleaning", 60))));
        c.setExperience("Six years cleaning commercial offices and office buildings in Halifax.");
        c.setQualifications(new ArrayList<>(List.of(
                new Qualification("CleanSafe Level 2 certification", EvidenceStatus.DECLARED_NO_EVIDENCE, null))));
        c.setEvidenceSummary("Certificate copy not yet uploaded.");
        c.setPartnershipPreference("Open to subcontracting partners for locations outside Halifax.");
        c.setOpenToPartnerships(true);
        c.setProfileConfirmedAt(Instant.now());
        c.setDemo(true);
        return c;
    }

    private static Company fundyClean() {
        Company c = new Company(DemoData.FUNDY_CLEAN, "Fundy Clean");
        c.setContactName("Sam Doucet");
        c.setServices(new ArrayList<>(List.of("Commercial office cleaning")));
        c.setSpecializations(new ArrayList<>(List.of("Small office buildings")));
        c.setServiceAreas(new ArrayList<>(List.of("Truro")));
        c.setCapacity(new ArrayList<>(List.of(new CapacityEntry("Truro", "Commercial office cleaning", 20))));
        c.setExperience("Four years cleaning offices in Truro and Colchester County.");
        c.setQualifications(new ArrayList<>(List.of(
                new Qualification("CleanSafe Level 2 certification", EvidenceStatus.EVIDENCED,
                        "Certificate CS2-4471 on file, valid to 2027"))));
        c.setEvidenceSummary("CleanSafe certificate CS2-4471 on file (private).");
        c.setPartnershipPreference("Interested in subcontracting for Truro-area work.");
        c.setOpenToPartnerships(true);
        c.setProfileConfirmedAt(Instant.now());
        c.setDemo(true);
        return c;
    }
}
