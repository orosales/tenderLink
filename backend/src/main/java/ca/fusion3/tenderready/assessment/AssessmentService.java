package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.ai.AiAnalysisException;
import ca.fusion3.tenderready.ai.ClaudeAnalysisClient;
import ca.fusion3.tenderready.ai.FixtureAnalysis;
import ca.fusion3.tenderready.assessment.AssessmentDtos.AssessmentView;
import ca.fusion3.tenderready.assessment.AssessmentDtos.Comparison;
import ca.fusion3.tenderready.assessment.AssessmentDtos.Ref;
import ca.fusion3.tenderready.assessment.Model.AiExtraction;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.Mode;
import ca.fusion3.tenderready.assessment.Model.Requirement;
import ca.fusion3.tenderready.collaboration.Invitation;
import ca.fusion3.tenderready.collaboration.InvitationService;
import ca.fusion3.tenderready.collaboration.InvitationState;
import ca.fusion3.tenderready.collaboration.ProposedContribution;
import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.common.AppProperties;
import ca.fusion3.tenderready.common.Text;
import ca.fusion3.tenderready.companies.Company;
import ca.fusion3.tenderready.companies.CompanyService;
import ca.fusion3.tenderready.tenders.DocumentBundle;
import ca.fusion3.tenderready.tenders.Tender;
import ca.fusion3.tenderready.tenders.TenderService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {
    private final AssessmentRepository assessments;
    private final DocumentExtractionRepository extractions;
    private final RequirementCorrectionRepository corrections;
    private final TenderService tenders;
    private final CompanyService companies;
    private final InvitationService invitations;
    private final ClaudeAnalysisClient claude;
    private final FixtureAnalysis fixtures;
    private final AppProperties props;
    private final AssessmentEngine engine = new AssessmentEngine();
    private final ExtractionValidator validator = new ExtractionValidator();

    public AssessmentService(AssessmentRepository assessments, DocumentExtractionRepository extractions,
                             RequirementCorrectionRepository corrections, TenderService tenders,
                             CompanyService companies, InvitationService invitations, ClaudeAnalysisClient claude,
                             FixtureAnalysis fixtures, AppProperties props) {
        this.assessments = assessments;
        this.extractions = extractions;
        this.corrections = corrections;
        this.tenders = tenders;
        this.companies = companies;
        this.invitations = invitations;
        this.claude = claude;
        this.fixtures = fixtures;
        this.props = props;
    }

    public Mode defaultMode() {
        return "live".equalsIgnoreCase(props.ai().mode()) ? Mode.LIVE : Mode.FIXTURE;
    }

    // ---------------- single-company assessment ----------------

    @Transactional
    public AssessmentView assess(Company active, UUID tenderId, Mode requestedMode) {
        Assessment a = assessSingle(active, tenders.get(tenderId), requestedMode == null ? defaultMode() : requestedMode);
        return view(a, active.getId());
    }

    private Assessment assessSingle(Company lead, Tender tender, Mode mode) {
        requireConfirmed(lead);
        DocumentBundle bundle = tenders.bundle(tender.getId());
        Extraction ex = extraction(tender, bundle, mode, lead);
        AssessmentEngine.Result result = engine.evaluate(ex, CompanyFacts.of(lead), List.of());
        Assessment a = new Assessment(tender.getId(), lead.getId());
        fill(a, lead, bundle, mode, ex, result);
        return assessments.save(a);
    }

    // ---------------- combined assessment after mutual confirmation ----------------

    @Transactional
    public AssessmentView reassessCombined(Company active, UUID invitationId) {
        Invitation inv = invitations.require(active, invitationId);
        if (inv.getState() != InvitationState.CONFIRMED_FOR_ASSESSMENT) {
            throw ApiException.conflict("NOT_CONFIRMED",
                    "Both companies must confirm the proposal before a combined assessment (current state: " + inv.getState() + ").");
        }
        Company lead = companies.get(inv.getSenderCompanyId());
        Company partner = companies.get(inv.getRecipientCompanyId());
        requireConfirmed(lead);
        Tender tender = tenders.get(inv.getTenderId());
        Mode mode = assessments.findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(tender.getId(), lead.getId())
                .map(Assessment::getMode).orElse(defaultMode());

        // Fresh baseline from the same inputs, so the before/after view compares like with like.
        Assessment baseline = assessSingle(lead, tender, mode);

        DocumentBundle bundle = tenders.bundle(tender.getId());
        Extraction ex = extraction(tender, bundle, mode, lead);
        ProposedContribution c = inv.getProposedContribution();
        List<PartnerContribution> team = List.of(new PartnerContribution(CompanyFacts.of(partner), c.area(), c.service(),
                c.weeklyHours(), c.partnerRole()));
        AssessmentEngine.Result result = engine.evaluate(ex, CompanyFacts.of(lead), team);
        Assessment a = new Assessment(tender.getId(), lead.getId());
        fill(a, lead, bundle, mode, ex, result);
        a.setInvitationId(inv.getId());
        a.setBaselineAssessmentId(baseline.getId());
        a.setPartnerRevisions(new HashMap<>(Map.of(partner.getId().toString(), partner.getProfileRevision())));
        return view(assessments.save(a), active.getId());
    }

    // ---------------- human corrections ----------------

    @Transactional
    public AssessmentView correct(Company active, UUID assessmentId, String requirementId, RequirementCorrection.Payload payload) {
        Assessment a = assessments.findById(assessmentId).orElseThrow(() -> ApiException.notFound("Assessment"));
        if (!a.getCompanyId().equals(active.getId())) {
            throw ApiException.forbidden("Only the assessed company can correct its requirements.");
        }
        if (a.getExtraction().requirements().stream().noneMatch(r -> r.id().equals(requirementId))) {
            throw ApiException.notFound("Requirement " + requirementId);
        }
        if (payload.weeklyHours() != null && (payload.weeklyHours() < 0 || payload.weeklyHours() > 10000)) {
            throw ApiException.badRequest("weeklyHours must be between 0 and 10000");
        }
        RequirementCorrection rc = corrections
                .findByTenderIdAndDocumentVersionAndRequirementId(a.getTenderId(), a.getDocumentVersion(), requirementId)
                .orElseGet(() -> new RequirementCorrection(a.getTenderId(), a.getDocumentVersion(), requirementId));
        rc.setCorrection(payload);
        rc.setCorrectedBy(active.getId());
        rc.setUpdatedAt(Instant.now());
        corrections.save(rc);
        if (a.getInvitationId() != null) {
            return reassessCombined(active, a.getInvitationId());
        }
        return view(assessSingle(active, tenders.get(a.getTenderId()), a.getMode()), active.getId());
    }

    // ---------------- reads ----------------

    @Transactional(readOnly = true)
    public AssessmentView get(Company active, UUID id) {
        Assessment a = assessments.findById(id).orElseThrow(() -> ApiException.notFound("Assessment"));
        return view(a, active.getId());
    }

    @Transactional(readOnly = true)
    public AssessmentView latest(Company active, UUID tenderId) {
        return assessments.findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(tenderId, active.getId())
                .map(a -> view(a, active.getId()))
                .orElse(null);
    }

    /** Latest single-company assessment re-evaluated against the company's current profile (no AI call). */
    @Transactional(readOnly = true)
    public Assessment latestEntity(UUID tenderId, UUID companyId) {
        return assessments.findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(tenderId, companyId)
                .orElse(null);
    }

    public AssessmentEngine engine() {
        return engine;
    }

    // ---------------- internals ----------------

    private Extraction extraction(Tender tender, DocumentBundle bundle, Mode mode, Company lead) {
        Extraction base = extractions.findByTenderIdAndDocumentVersionAndMode(tender.getId(), bundle.version(), mode)
                .map(DocumentExtraction::getResult)
                .orElseGet(() -> {
                    Extraction fresh = interpret(tender, bundle, mode, lead);
                    extractions.save(new DocumentExtraction(tender.getId(), bundle.version(), mode,
                            mode == Mode.LIVE ? claude.model() : "fixture", fresh));
                    return fresh;
                });
        return applyCorrections(base, tender.getId(), bundle.version());
    }

    private Extraction interpret(Tender tender, DocumentBundle bundle, Mode mode, Company lead) {
        if (mode == Mode.FIXTURE) {
            if (!tender.isDemo() || !lead.isDemo()) {
                throw ApiException.unprocessable("FIXTURE_NOT_APPLICABLE",
                        "Demonstration analysis only applies to the bundled sample scenario. Use live AI mode for this tender.");
            }
            AiExtraction ai = fixtures.forDocument(tender.getSourceId(), bundle.version())
                    .orElseThrow(() -> ApiException.unprocessable("FIXTURE_MISMATCH",
                            "The bundled demonstration analysis does not match this document version."));
            return validator.validate(ai, bundle.text(), bundle.complete());
        }
        if (!claude.available()) {
            throw ApiException.unprocessable("AI_NOT_CONFIGURED",
                    "Live AI mode needs ANTHROPIC_API_KEY on the backend. Demonstration analysis is only available for the sample scenario.");
        }
        String text = bundle.text();
        boolean complete = bundle.complete();
        int max = props.ai().maxDocumentChars();
        if (text.length() > max) {
            text = text.substring(0, max);
            complete = false;
        }
        try {
            AiExtraction ai = claude.extract(metadata(tender), text);
            Extraction ex = validator.validate(ai, text, complete);
            if (bundle.text().length() > max) {
                List<String> unknowns = new ArrayList<>(ex.unknowns());
                unknowns.add(0, "Only the first " + max + " characters of the documents were analysed (prototype size limit).");
                ex = new Extraction(false, ex.documentCompletenessNote(), ex.scopeSummary(), ex.deadlines(), ex.requirements(),
                        ex.partnershipPermission(), ex.partnershipArrangement(), ex.partnershipSourceReference(),
                        ex.partnershipExcerpt(), ex.partnershipExcerptVerified(), ex.partnershipConditions(),
                        ex.jointVentureNote(), unknowns);
            }
            return ex;
        } catch (AiAnalysisException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_FAILED", e.getMessage());
        }
    }

    private static String metadata(Tender t) {
        DateTimeFormatter f = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return "Title: " + t.getTitle()
                + "\nBuyer: " + Text.truncate(String.valueOf(t.getBuyer()), 300)
                + "\nDelivery regions (notice): " + (t.getDeliveryAreas() == null ? "unknown" : String.join(", ", t.getDeliveryAreas()))
                + "\nClosing: " + (t.getClosingAt() == null ? "unknown" : f.format(t.getClosingAt().atOffset(ZoneOffset.UTC)));
    }

    private Extraction applyCorrections(Extraction ex, UUID tenderId, String version) {
        Map<String, RequirementCorrection> byReq = corrections.findByTenderIdAndDocumentVersion(tenderId, version).stream()
                .collect(Collectors.toMap(RequirementCorrection::getRequirementId, Function.identity()));
        if (byReq.isEmpty()) return ex;
        List<Requirement> reqs = ex.requirements().stream().map(r -> {
            RequirementCorrection rc = byReq.get(r.id());
            if (rc == null) return r;
            RequirementCorrection.Payload p = rc.getCorrection();
            List<String> notes = new ArrayList<>(r.validationNotes() == null ? List.of() : r.validationNotes());
            notes.add("Human-reviewed correction" + (Text.isBlank(p.reviewerNote()) ? "" : ": " + p.reviewerNote()));
            return new Requirement(r.id(),
                    p.description() != null ? p.description() : r.description(),
                    p.category() != null ? p.category() : r.category(),
                    p.kind() != null ? p.kind() : r.kind(),
                    p.appliesTo() != null ? p.appliesTo() : r.appliesTo(),
                    p.area() != null ? p.area() : r.area(),
                    p.service() != null ? p.service() : r.service(),
                    p.weeklyHours() != null ? (p.weeklyHours() == 0 ? null : p.weeklyHours()) : r.weeklyHours(),
                    p.qualificationName() != null ? p.qualificationName() : r.qualificationName(),
                    p.matchTerms() != null ? p.matchTerms() : r.matchTerms(),
                    r.sourceReference(), r.sourceExcerpt(), r.excerptVerified(), true, notes);
        }).toList();
        return ex.withRequirements(reqs);
    }

    private void fill(Assessment a, Company lead, DocumentBundle bundle, Mode mode, Extraction ex, AssessmentEngine.Result result) {
        a.setProfileRevision(lead.getProfileRevision());
        a.setDocumentId(bundle.primaryDocumentId());
        a.setDocumentVersion(bundle.version());
        a.setMode(mode);
        a.setModel(mode == Mode.LIVE ? claude.model() : "fixture");
        a.setDocumentComplete(ex.documentComplete());
        a.setReadiness(result.readiness());
        a.setExtraction(ex);
        a.setFindings(result.findings());
        a.setActions(result.actions());
        List<String> warnings = new ArrayList<>(result.warnings());
        a.setWarnings(warnings);
        a.setPartnershipPermission(ex.partnershipPermission());
    }

    private static void requireConfirmed(Company c) {
        if (!c.isConfirmed()) {
            throw ApiException.unprocessable("PROFILE_NOT_CONFIRMED",
                    c.getName() + " must confirm its profile first: assessments only use confirmed profile information.");
        }
    }

    AssessmentView view(Assessment a, UUID viewer) {
        Invitation inv = null;
        if (a.getInvitationId() != null) {
            try {
                inv = invitations.require(companies.get(viewer), a.getInvitationId());
            } catch (ApiException e) {
                throw ApiException.forbidden("This assessment belongs to another company.");
            }
        } else if (!a.getCompanyId().equals(viewer)) {
            throw ApiException.forbidden("This assessment belongs to another company.");
        }
        Tender t = tenders.get(a.getTenderId());
        Company lead = companies.get(a.getCompanyId());
        List<Ref> partners = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        if (lead.getProfileRevision() != a.getProfileRevision()) stale.add(lead.getName() + " profile changed since this assessment.");
        if (!lead.isConfirmed()) stale.add(lead.getName() + " profile is awaiting confirmation.");
        for (var e : a.getPartnerRevisions().entrySet()) {
            Company p = companies.get(UUID.fromString(e.getKey()));
            partners.add(new Ref(p.getId(), p.getName()));
            if (p.getProfileRevision() != e.getValue()) stale.add(p.getName() + " (partner) profile changed since this assessment.");
            if (!p.isOpenToPartnerships()) stale.add(p.getName() + " is no longer open to partnerships.");
        }
        if (inv != null && inv.getState() != InvitationState.CONFIRMED_FOR_ASSESSMENT) {
            stale.add("The collaboration is now " + inv.getState() + ".");
        }
        DocumentBundle bundle = tenders.bundle(t.getId());
        if (!bundle.version().equals(a.getDocumentVersion())) stale.add("The tender documents changed since this assessment.");
        boolean newerCorrection = corrections.findByTenderIdAndDocumentVersion(t.getId(), a.getDocumentVersion()).stream()
                .anyMatch(c -> c.getUpdatedAt().isAfter(a.getCreatedAt()));
        if (newerCorrection) stale.add("A requirement was corrected after this assessment.");

        List<Comparison> comparison = null;
        if (a.getBaselineAssessmentId() != null) {
            comparison = assessments.findById(a.getBaselineAssessmentId()).map(b -> compare(b, a)).orElse(null);
        }
        String modeLabel = a.getMode() == Mode.FIXTURE
                ? "Demonstration analysis (bundled fixture; capacity and evidence checks recalculated from current profiles)"
                : "Live AI analysis (" + a.getModel() + "); capacity and evidence checks are deterministic";
        return new AssessmentView(a.getId(), t.getId(), t.getTitle(), t.isDemo(), new Ref(lead.getId(), lead.getName()),
                partners, a.getInvitationId(), a.getInvitationId() != null, a.getMode(), modeLabel, a.getModel(),
                a.isDocumentComplete(), bundle.sourceNames(), bundle.limitations(), a.getDocumentVersion(),
                a.getReadiness(), AssessmentDtos.readinessLabel(a.getReadiness()), a.getExtraction(), a.getFindings(),
                a.getActions(), a.getWarnings(), a.getCreatedAt(), !stale.isEmpty(), stale,
                a.getBaselineAssessmentId(), comparison);
    }

    private static List<Comparison> compare(Assessment before, Assessment after) {
        Map<String, Finding> b = before.getFindings().stream().collect(Collectors.toMap(Finding::requirementId, f -> f, (x, y) -> x));
        return after.getFindings().stream().map(f -> {
            Finding old = b.get(f.requirementId());
            return new Comparison(f.requirementId(), f.description(),
                    old == null ? null : old.status(), old != null && old.conditional(),
                    f.status(), f.conditional(),
                    old == null || old.status() != f.status() || old.conditional() != f.conditional());
        }).toList();
    }
}
