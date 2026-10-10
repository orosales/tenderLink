package ca.fusion3.tenderready.matching;

import ca.fusion3.tenderready.assessment.CompanyFacts;
import ca.fusion3.tenderready.assessment.Model.AppliesTo;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.FindingStatus;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.Requirement;
import ca.fusion3.tenderready.assessment.Model.RequirementKind;
import ca.fusion3.tenderready.assessment.ProfileMatching;
import ca.fusion3.tenderready.companies.Qualification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shortlists stored, opted-in, confirmed profiles whose declared capacity addresses specific location gaps.
 * Ranking is a recommendation by declared coverage, not a win probability or an eligibility score.
 */
public final class PartnerMatcher {

    public record Gap(String requirementId, String description, String area, String service, int requiredHours,
                      int leadHours, int shortfall) {}

    public record GapCoverage(String requirementId, String area, String service, int shortfall, int declaredHours,
                              int suggestedHours, boolean fullyCovers) {}

    public record CredentialNote(String requirementId, String requirement, FindingStatus status, String note) {}

    public record Candidate(UUID companyId, String companyName, List<String> services, List<String> serviceAreas,
                            String partnershipPreference, List<GapCoverage> addresses, List<CredentialNote> credentials,
                            double coverageRatio, String label) {}

    public record Result(List<Gap> gaps, List<Candidate> candidates, int excludedOptedOutOrUnconfirmed, boolean feasible,
                         String permissionLabel) {}

    public List<Gap> gaps(Extraction ex, List<Finding> leadFindings, CompanyFacts lead) {
        Map<String, Requirement> reqs = ex.requirements().stream().collect(Collectors.toMap(Requirement::id, Function.identity()));
        List<Gap> gaps = new ArrayList<>();
        for (Finding f : leadFindings) {
            if (f.status() != FindingStatus.GAP || f.kind() != RequirementKind.CAPACITY) continue;
            Requirement r = reqs.get(f.requirementId());
            if (r == null || r.weeklyHours() == null || r.area() == null) continue;
            int leadHours = ProfileMatching.hoursIn(lead.capacity(), r.area(), r.service());
            gaps.add(new Gap(r.id(), r.description(), r.area(), r.service(), r.weeklyHours(), leadHours,
                    Math.max(0, r.weeklyHours() - leadHours)));
        }
        return gaps;
    }

    public Result match(Extraction ex, List<Gap> gaps, CompanyFacts lead, List<CompanyFacts> others) {
        PartnershipPermission permission = ex.partnershipPermission();
        String label = switch (permission) {
            case UNKNOWN -> "Potential fit: buyer clarification required";
            case PERMITTED_WITH_CONDITIONS -> "Potential fit: subject to partnering conditions";
            case PROHIBITED -> "Not a feasible path: the document prohibits this arrangement";
        };
        List<CompanyFacts> eligible = others.stream()
                .filter(c -> !c.id().equals(lead.id()))
                .filter(c -> c.openToPartnerships() && c.confirmed())
                .toList();
        int excluded = (int) others.stream().filter(c -> !c.id().equals(lead.id())).count() - eligible.size();
        if (permission == PartnershipPermission.PROHIBITED) {
            return new Result(gaps, List.of(), excluded, false, label);
        }
        List<Requirement> perParticipant = ex.requirements().stream()
                .filter(r -> r.kind() == RequirementKind.QUALIFICATION && r.appliesTo() == AppliesTo.EACH_DELIVERY_COMPANY)
                .toList();
        List<Candidate> out = new ArrayList<>();
        int totalShortfall = gaps.stream().mapToInt(Gap::shortfall).sum();
        for (CompanyFacts c : eligible) {
            List<GapCoverage> covers = new ArrayList<>();
            for (Gap g : gaps) {
                int declared = ProfileMatching.hoursIn(c.capacity(), g.area(), g.service());
                if (declared > 0 && g.shortfall() > 0) {
                    covers.add(new GapCoverage(g.requirementId(), g.area(), g.service(), g.shortfall(), declared,
                            Math.min(declared, g.shortfall()), declared >= g.shortfall()));
                }
            }
            if (covers.isEmpty()) continue;
            List<CredentialNote> creds = perParticipant.stream().map(r -> credential(r, c)).toList();
            double ratio = totalShortfall == 0 ? 0 : covers.stream().mapToInt(GapCoverage::suggestedHours).sum() / (double) totalShortfall;
            out.add(new Candidate(c.id(), c.name(), c.services(), c.serviceAreas(), null, covers, creds, ratio, label));
        }
        out.sort(Comparator.comparingDouble(Candidate::coverageRatio).reversed().thenComparing(Candidate::companyName));
        return new Result(gaps, out, excluded, true, label);
    }

    private static CredentialNote credential(Requirement r, CompanyFacts c) {
        Optional<Qualification> q = ProfileMatching.findQualification(c.qualifications(), r.qualificationName(), r.matchTerms());
        String name = r.qualificationName() == null ? r.description() : r.qualificationName();
        if (q.isEmpty()) {
            return new CredentialNote(r.id(), name, FindingStatus.UNKNOWN, "Not stated in the candidate's profile: would need to be evidenced");
        }
        return switch (q.get().status()) {
            case EVIDENCED -> new CredentialNote(r.id(), name, FindingStatus.MATCH, "Evidence noted in the candidate's confirmed profile");
            case DECLARED_NO_EVIDENCE -> new CredentialNote(r.id(), name, FindingStatus.MISSING_EVIDENCE, "Declared; evidence not yet provided");
            case UNKNOWN -> new CredentialNote(r.id(), name, FindingStatus.UNKNOWN, "Listed with unknown status");
        };
    }
}
