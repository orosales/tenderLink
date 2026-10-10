package ca.fusion3.tenderready.matching;

import ca.fusion3.tenderready.ai.AiAnalysisException;
import ca.fusion3.tenderready.ai.ClaudeAnalysisClient;
import ca.fusion3.tenderready.assessment.Assessment;
import ca.fusion3.tenderready.assessment.AssessmentEngine;
import ca.fusion3.tenderready.assessment.AssessmentService;
import ca.fusion3.tenderready.assessment.CompanyFacts;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.FindingStatus;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.RequirementKind;
import ca.fusion3.tenderready.collaboration.Invitation;
import ca.fusion3.tenderready.collaboration.InvitationRepository;
import ca.fusion3.tenderready.collaboration.ProposedContribution;
import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.companies.Company;
import ca.fusion3.tenderready.companies.CompanyRepository;
import ca.fusion3.tenderready.matching.PartnerMatcher.Candidate;
import ca.fusion3.tenderready.matching.PartnerMatcher.Gap;
import ca.fusion3.tenderready.matching.PartnerMatcher.GapCoverage;
import ca.fusion3.tenderready.tenders.TenderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingService {
    public static final String DISCLAIMER = "Ranking is a recommendation based on declared profile data. It is not a win "
            + "probability or a legal eligibility score. Availability is declared and needs partner confirmation.";

    public record Unresolved(String requirementId, String description, FindingStatus status, String note) {}

    public record ExistingInvitation(UUID id, String state, String direction) {}

    public record Recommendation(Candidate candidate, String partnershipPreference, String explanation,
                                 String explanationSource, ProposedContribution suggestedContribution,
                                 ExistingInvitation existingInvitation) {}

    public record RecommendationsView(UUID assessmentId, boolean recalculatedFromCurrentProfile, String disclaimer,
                                      PartnershipPermission partnershipPermission, String permissionLabel,
                                      String partnershipExcerpt, String partnershipSourceReference,
                                      List<String> partnershipConditions, String jointVentureNote, boolean feasible,
                                      List<Gap> gaps, List<Recommendation> recommendations,
                                      int excludedOptedOutOrUnconfirmed, List<Unresolved> unresolvedForLead,
                                      List<String> alternatives) {}

    private final AssessmentService assessments;
    private final CompanyRepository companies;
    private final InvitationRepository invitations;
    private final TenderService tenders;
    private final ClaudeAnalysisClient claude;
    private final ObjectMapper mapper;
    private final PartnerMatcher matcher = new PartnerMatcher();

    public MatchingService(AssessmentService assessments, CompanyRepository companies, InvitationRepository invitations,
                           TenderService tenders, ClaudeAnalysisClient claude, ObjectMapper mapper) {
        this.assessments = assessments;
        this.companies = companies;
        this.invitations = invitations;
        this.tenders = tenders;
        this.claude = claude;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RecommendationsView recommend(Company active, UUID tenderId, boolean aiExplanation) {
        tenders.get(tenderId);
        Assessment a = assessments.latestEntity(tenderId, active.getId());
        if (a == null) {
            throw ApiException.unprocessable("NO_ASSESSMENT", "Run the gap assessment for this tender first.");
        }
        CompanyFacts lead = CompanyFacts.of(active);
        // Recalculate from the current profile: a seeded or earlier positive match is never reused after edits.
        AssessmentEngine.Result current = assessments.engine().evaluate(a.getExtraction(), lead, List.of());
        List<Gap> gaps = matcher.gaps(a.getExtraction(), current.findings(), lead);
        List<CompanyFacts> others = companies.findAll().stream().map(CompanyFacts::of).toList();
        PartnerMatcher.Result result = matcher.match(a.getExtraction(), gaps, lead, others);

        Map<UUID, Company> byId = companies.findAll().stream().collect(Collectors.toMap(Company::getId, c -> c));
        List<Invitation> sent = invitations.findByTenderIdAndSenderCompanyId(tenderId, active.getId());
        List<Recommendation> recs = new ArrayList<>();
        for (Candidate c : result.candidates()) {
            String template = explain(c, a.getExtraction().partnershipConditions(), result.permissionLabel());
            String explanation = template;
            String source = "Rule-based summary of stored profile records";
            if (aiExplanation && claude.available()) {
                try {
                    explanation = claude.explainFit(facts(c, gaps, a));
                    source = "AI explanation (" + claude.model() + ") grounded in the stored records shown";
                } catch (AiAnalysisException e) {
                    source = "Rule-based summary (" + e.getMessage() + ")";
                }
            }
            GapCoverage first = c.addresses().get(0);
            ProposedContribution suggestion = new ProposedContribution(first.area(), first.service() == null ? "Commercial cleaning" : first.service(),
                    first.suggestedHours(), "Subcontractor: " + (first.service() == null ? "services" : first.service()) + " in " + first.area(),
                    "Lead bidder (prime contractor)", "Deliver " + first.suggestedHours() + " weekly labour hours in " + first.area()
                    + "; provide own qualification evidence; follow the lead bidder's quality and reporting requirements.",
                    c.addresses().stream().map(GapCoverage::requirementId).toList());
            ExistingInvitation existing = sent.stream().filter(i -> i.getRecipientCompanyId().equals(c.companyId()))
                    .filter(i -> i.getState().isActive())
                    .findFirst().map(i -> new ExistingInvitation(i.getId(), i.getState().name(), "SENT")).orElse(null);
            Company company = byId.get(c.companyId());
            recs.add(new Recommendation(c, company == null ? null : company.getPartnershipPreference(), explanation, source,
                    suggestion, existing));
        }
        List<String> gapIds = gaps.stream().map(Gap::requirementId).toList();
        List<Unresolved> unresolved = current.findings().stream()
                .filter(f -> f.status() != FindingStatus.MATCH && !gapIds.contains(f.requirementId()))
                .map(f -> new Unresolved(f.requirementId(), f.description(), f.status(), f.action()))
                .toList();
        List<String> alternatives = new ArrayList<>();
        if (recs.isEmpty()) {
            if (!result.feasible()) alternatives.add("Ask the buyer to clarify whether any partnering arrangement is acceptable.");
            gaps.forEach(g -> alternatives.add("Establish your own coverage of " + g.shortfall() + " h/week in " + g.area()
                    + " and update your profile."));
            alternatives.add("Ask the buyer whether the scope or locations can be split.");
            alternatives.add("Review and correct the extracted requirements if a location or hour figure is wrong.");
        }
        var ex = a.getExtraction();
        return new RecommendationsView(a.getId(), a.getProfileRevision() != active.getProfileRevision(), DISCLAIMER,
                ex.partnershipPermission(), result.permissionLabel(), ex.partnershipExcerpt(),
                ex.partnershipSourceReference(), ex.partnershipConditions(), ex.jointVentureNote(), result.feasible(),
                gaps, recs, result.excludedOptedOutOrUnconfirmed(), unresolved, alternatives);
    }

    private static String explain(Candidate c, List<String> conditions, String label) {
        String covers = c.addresses().stream()
                .map(g -> g.requirementId() + " (" + g.area() + "): declares " + g.declaredHours() + " h/week against a "
                        + g.shortfall() + " h/week shortfall" + (g.fullyCovers() ? "" : ", partial"))
                .collect(Collectors.joining("; "));
        String creds = c.credentials().stream()
                .map(n -> n.requirement() + ": " + n.note().toLowerCase())
                .collect(Collectors.joining("; "));
        return c.companyName() + " has a confirmed profile, is open to partnerships, and " + covers + "."
                + (creds.isEmpty() ? "" : " Per-company requirements: " + creds + ".")
                + " Declared availability needs partner confirmation. " + label
                + (conditions == null || conditions.isEmpty() ? "" : " (" + String.join("; ", conditions) + ")") + ".";
    }

    private String facts(Candidate c, List<Gap> gaps, Assessment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gaps", gaps);
        m.put("candidate", Map.of("name", c.companyName(), "services", c.services(), "serviceAreas", c.serviceAreas(),
                "gapCoverage", c.addresses(), "perCompanyCredentials", c.credentials()));
        m.put("partnershipPermission", a.getExtraction().partnershipPermission());
        m.put("partnershipConditions", a.getExtraction().partnershipConditions());
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isCapacityGap(Finding f) {
        return f.status() == FindingStatus.GAP && f.kind() == RequirementKind.CAPACITY;
    }
}
