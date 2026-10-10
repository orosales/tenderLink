package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.assessment.Model.ActionItem;
import ca.fusion3.tenderready.assessment.Model.AppliesTo;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.FindingStatus;
import ca.fusion3.tenderready.assessment.Model.MemberEvaluation;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.Readiness;
import ca.fusion3.tenderready.assessment.Model.Requirement;
import ca.fusion3.tenderready.assessment.Model.RequirementCategory;
import ca.fusion3.tenderready.companies.Qualification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Deterministic evaluation of extracted requirements against declared, confirmed profile data.
 * The AI never decides hours or qualifications: explicit capacity numbers are compared here, per location,
 * and credentials are evaluated per company, never pooled.
 */
public final class AssessmentEngine {
    public static final String LEAD_ROLE = "Lead bidder";

    public record Result(List<Finding> findings, List<ActionItem> actions, Readiness readiness, List<String> warnings) {}

    public Result evaluate(Extraction ex, CompanyFacts lead, List<PartnerContribution> partners) {
        List<Finding> findings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Requirement r : ex.requirements()) {
            findings.add(switch (r.kind()) {
                case CAPACITY -> capacity(r, ex, lead, partners);
                case QUALIFICATION -> qualification(r, lead, partners);
                case PARTNERING -> partnering(r, ex, partners);
                case OTHER -> other(r, lead);
            });
        }
        if (!ex.documentComplete()) {
            warnings.add("Document unavailable / assessment incomplete: no complete readiness verdict is given.");
        }
        findings.stream().filter(f -> !f.excerptVerified())
                .forEach(f -> warnings.add(f.requirementId() + ": quoted excerpt was not found in the document text (unsupported)."));
        List<ActionItem> actions = actions(findings, ex);
        return new Result(findings, actions, readiness(findings, ex.documentComplete()), warnings);
    }

    // ---------- capacity ----------

    private Finding capacity(Requirement r, Extraction ex, CompanyFacts lead, List<PartnerContribution> partners) {
        if (r.weeklyHours() == null || r.area() == null || r.area().isBlank()) {
            return finding(r, FindingStatus.UNKNOWN, false, "Not applicable",
                    "The document text does not state an explicit location and number of weekly hours for this requirement.",
                    "Confirm the required hours and location with the buyer or correct this requirement.", List.of());
        }
        int required = r.weeklyHours();
        List<MemberEvaluation> members = new ArrayList<>();
        boolean leadHasAnyCapacityData = !lead.capacity().isEmpty() || !lead.serviceAreas().isEmpty();
        int leadHours = ProfileMatching.hoursIn(lead.capacity(), r.area(), r.service());
        members.add(new MemberEvaluation(lead.id().toString(), lead.name(), LEAD_ROLE,
                leadHours >= required ? FindingStatus.MATCH : (leadHasAnyCapacityData ? FindingStatus.GAP : FindingStatus.UNKNOWN),
                leadHours, leadHours > 0 ? leadHours + " h/week declared in " + r.area()
                        : "No declared capacity in " + r.area()));

        PartnershipPermission permission = ex.partnershipPermission();
        int partnerHours = 0;
        for (PartnerContribution pc : partners) {
            if (!ProfileMatching.areaMatches(r.area(), pc.area()) || !ProfileMatching.serviceMatches(r.service(), pc.service())) {
                continue;
            }
            CompanyFacts p = pc.partner();
            int declared = ProfileMatching.hoursIn(p.capacity(), r.area(), r.service());
            int counted = Math.min(declared, pc.weeklyHours() == null ? 0 : pc.weeklyHours());
            String note;
            if (permission == PartnershipPermission.PROHIBITED) {
                counted = 0;
                note = "Not counted: the document prohibits this arrangement";
            } else if (!p.confirmed()) {
                counted = 0;
                note = "Not counted: partner profile is not confirmed";
            } else if (!p.openToPartnerships()) {
                counted = 0;
                note = "Not counted: partner is no longer open to partnerships";
            } else if (declared < (pc.weeklyHours() == null ? 0 : pc.weeklyHours())) {
                note = "Committed " + pc.weeklyHours() + " h but only " + declared + " h currently declared in " + r.area();
            } else {
                note = "Confirmed contribution of " + counted + " h/week in " + r.area() + " (declared availability; partner-confirmed)";
            }
            partnerHours += counted;
            members.add(new MemberEvaluation(p.id().toString(), p.name(), pc.role(),
                    counted > 0 ? FindingStatus.MATCH : FindingStatus.GAP, counted, note));
        }

        int total = leadHours + partnerHours;
        String evidence = members.stream()
                .map(m -> m.companyName() + ": " + (m.hoursCounted() == null ? 0 : m.hoursCounted()) + " h/week in " + r.area())
                .collect(Collectors.joining("; "));
        if (total >= required) {
            boolean viaPartner = leadHours < required;
            boolean conditional = viaPartner && permission != PartnershipPermission.PROHIBITED;
            String expl = required + " h/week required in " + r.area() + "; " + total + " h/week available at this location.";
            if (viaPartner) {
                expl += " Coverage depends on a partner contribution"
                        + (permission == PartnershipPermission.UNKNOWN
                        ? " and the document does not establish that partnering is permitted: buyer clarification required."
                        : " and remains subject to the partnering conditions (e.g. buyer approval).");
            }
            return finding(r, FindingStatus.MATCH, conditional, evidence, expl,
                    viaPartner ? "Keep the partner's contribution confirmed and satisfy the partnering conditions." : "None",
                    members);
        }
        if (!leadHasAnyCapacityData && partners.isEmpty()) {
            return finding(r, FindingStatus.UNKNOWN, false, "No capacity declared in the profile",
                    "The profile declares no service areas or capacity, so coverage of " + r.area() + " cannot be assessed.",
                    "Add service areas and weekly capacity by location to your profile.", members);
        }
        int shortfall = required - total;
        String expl = required + " h/week required in " + r.area() + "; " + total + " h/week declared at this location (short by "
                + shortfall + " h). Hours declared in other locations are not counted.";
        String action = permission == PartnershipPermission.PROHIBITED
                ? "Establish your own coverage in " + r.area() + "; the document prohibits the partnering arrangement."
                : "Explore permitted subcontracting for " + shortfall + " h/week in " + r.area() + " or establish own coverage.";
        return finding(r, FindingStatus.GAP, false, evidence, expl, action, members);
    }

    // ---------- qualifications ----------

    private Finding qualification(Requirement r, CompanyFacts lead, List<PartnerContribution> partners) {
        List<MemberEvaluation> members = new ArrayList<>();
        members.add(qualificationFor(r, lead, LEAD_ROLE));
        boolean perParticipant = r.appliesTo() == AppliesTo.EACH_DELIVERY_COMPANY;
        if (perParticipant) {
            partners.stream().map(PartnerContribution::partner).distinct()
                    .forEach(p -> members.add(qualificationFor(r, p, rolesOf(partners, p))));
        }
        FindingStatus status = members.stream().map(MemberEvaluation::status)
                .max(Comparator.comparingInt(AssessmentEngine::severity)).orElse(FindingStatus.UNKNOWN);
        String who = switch (r.appliesTo()) {
            case EACH_DELIVERY_COMPANY -> "Applies to every proposed delivery company; each is evaluated separately.";
            case LEAD_BIDDER -> "Applies to the lead bidder only; partner credentials are not substituted.";
            default -> "The document does not say who must hold it; evaluated against the lead bidder only, credentials are not combined.";
        };
        String evidence = members.stream().map(m -> m.companyName() + ": " + m.note()).collect(Collectors.joining("; "));
        String action = switch (status) {
            case MATCH -> "Include the evidence in the bid package.";
            case MISSING_EVIDENCE -> "Upload or attach evidence of " + label(r) + " for: "
                    + members.stream().filter(m -> m.status() == FindingStatus.MISSING_EVIDENCE).map(MemberEvaluation::companyName).collect(Collectors.joining(", ")) + ".";
            case UNKNOWN -> "Confirm whether " + label(r) + " is held and record it in the profile: "
                    + members.stream().filter(m -> m.status() == FindingStatus.UNKNOWN).map(MemberEvaluation::companyName).collect(Collectors.joining(", ")) + ".";
            case GAP -> "Obtain " + label(r) + " before bidding.";
        };
        return finding(r, status, false, evidence, who, action, members);
    }

    private MemberEvaluation qualificationFor(Requirement r, CompanyFacts c, String role) {
        Optional<Qualification> q = ProfileMatching.findQualification(c.qualifications(), r.qualificationName(), r.matchTerms());
        if (q.isEmpty()) {
            return new MemberEvaluation(c.id().toString(), c.name(), role, FindingStatus.UNKNOWN, null,
                    "not stated in profile (unknown, not assumed missing)");
        }
        return switch (q.get().status()) {
            case EVIDENCED -> new MemberEvaluation(c.id().toString(), c.name(), role, FindingStatus.MATCH, null,
                    "'" + q.get().name() + "' with evidence noted in confirmed profile");
            case DECLARED_NO_EVIDENCE -> new MemberEvaluation(c.id().toString(), c.name(), role, FindingStatus.MISSING_EVIDENCE, null,
                    "'" + q.get().name() + "' declared, evidence not yet provided");
            case UNKNOWN -> new MemberEvaluation(c.id().toString(), c.name(), role, FindingStatus.UNKNOWN, null,
                    "'" + q.get().name() + "' listed with unknown status");
        };
    }

    private static String rolesOf(List<PartnerContribution> partners, CompanyFacts p) {
        return partners.stream().filter(pc -> pc.partner().equals(p)).map(PartnerContribution::role).distinct()
                .collect(Collectors.joining(", "));
    }

    // ---------- partnering ----------

    private Finding partnering(Requirement r, Extraction ex, List<PartnerContribution> partners) {
        String conditions = ex.partnershipConditions() == null || ex.partnershipConditions().isEmpty()
                ? "" : " Conditions: " + String.join("; ", ex.partnershipConditions()) + ".";
        if (partners.isEmpty()) {
            return finding(r, FindingStatus.UNKNOWN, false, "No partner proposed",
                    "Applies only if a subcontractor or partner is proposed." + conditions,
                    "If you propose a partner, follow the partnering conditions.", List.of());
        }
        String names = partners.stream().map(pc -> pc.partner().name()).distinct().collect(Collectors.joining(", "));
        List<MemberEvaluation> members = partners.stream().map(PartnerContribution::partner).distinct()
                .map(p -> new MemberEvaluation(p.id().toString(), p.name(), rolesOf(partners, p),
                        FindingStatus.UNKNOWN, null, "Partner confirmed for assessment; buyer approval not evidenced"))
                .toList();
        return switch (ex.partnershipPermission()) {
            case PROHIBITED -> finding(r, FindingStatus.GAP, false, "Proposed partner(s): " + names,
                    "The document prohibits this arrangement, so the proposed partner cannot be relied on.",
                    "Do not rely on the partner; seek buyer clarification or self-perform.", members);
            case UNKNOWN -> finding(r, FindingStatus.UNKNOWN, false, "Proposed partner(s): " + names,
                    "The document does not establish whether this arrangement is permitted: buyer clarification required.",
                    "Ask the buyer whether subcontracting to " + names + " is permitted.", members);
            case PERMITTED_WITH_CONDITIONS -> finding(r, FindingStatus.UNKNOWN, false, "Proposed partner(s): " + names,
                    "Partnering is permitted only with conditions that are not yet evidenced." + conditions,
                    "Name " + names + " in the proposal and obtain the buyer's written approval before award.", members);
        };
    }

    // ---------- other (scored and general) ----------

    private Finding other(Requirement r, CompanyFacts lead) {
        Optional<String> support = ProfileMatching.findSupportingText(lead, r.matchTerms());
        String scoredNote = r.category() == RequirementCategory.SCORED_CRITERION
                ? " This is a scored criterion: it affects points, not eligibility." : "";
        if (support.isPresent()) {
            return finding(r, FindingStatus.MATCH, false, support.get(),
                    "The confirmed profile contains information relevant to this requirement." + scoredNote,
                    "Prepare supporting detail for the proposal.",
                    List.of(new MemberEvaluation(lead.id().toString(), lead.name(), LEAD_ROLE, FindingStatus.MATCH, null, support.get())));
        }
        return finding(r, FindingStatus.UNKNOWN, false, "No relevant information in the confirmed profile",
                "Nothing in the confirmed profile addresses this requirement; this is unknown, not a failure." + scoredNote,
                "Add relevant experience or evidence to the profile, or review manually.",
                List.of(new MemberEvaluation(lead.id().toString(), lead.name(), LEAD_ROLE, FindingStatus.UNKNOWN, null, "Not stated")));
    }

    // ---------- summary ----------

    private static List<ActionItem> actions(List<Finding> findings, Extraction ex) {
        List<ActionItem> out = new ArrayList<>();
        if (!ex.documentComplete()) {
            out.add(new ActionItem(0, "Obtain the complete solicitation documents before relying on this assessment.", null));
        }
        findings.stream()
                .filter(f -> f.status() != FindingStatus.MATCH || f.conditional())
                .sorted(Comparator.comparingInt((Finding f) -> -severity(f.status()))
                        .thenComparingInt(f -> categoryRank(f.category())))
                .forEach(f -> out.add(new ActionItem(categoryRank(f.category()) + 1, f.action(), f.requirementId())));
        return out;
    }

    private static Readiness readiness(List<Finding> findings, boolean complete) {
        if (!complete) return Readiness.INCOMPLETE_ASSESSMENT;
        boolean gap = findings.stream().anyMatch(f -> f.status() == FindingStatus.GAP
                && f.category() != RequirementCategory.SCORED_CRITERION);
        if (gap) return Readiness.GAPS_TO_RESOLVE;
        boolean open = findings.stream().anyMatch(f -> f.status() != FindingStatus.MATCH || f.conditional());
        return open ? Readiness.CONDITIONS_OUTSTANDING : Readiness.NO_GAPS_IN_ASSESSED_REQUIREMENTS;
    }

    static int severity(FindingStatus s) {
        return switch (s) {
            case MATCH -> 0;
            case UNKNOWN -> 1;
            case MISSING_EVIDENCE -> 2;
            case GAP -> 3;
        };
    }

    private static int categoryRank(RequirementCategory c) {
        return switch (c) {
            case MANDATORY_ELIGIBILITY -> 0;
            case DELIVERY_CONSTRAINT -> 1;
            case SCORED_CRITERION -> 2;
        };
    }

    private static String label(Requirement r) {
        return r.qualificationName() == null || r.qualificationName().isBlank() ? "the required qualification" : r.qualificationName();
    }

    private static Finding finding(Requirement r, FindingStatus status, boolean conditional, String evidence,
                                   String explanation, String action, List<MemberEvaluation> members) {
        return new Finding(r.id(), r.description(), r.category(), r.kind(), r.appliesTo(), r.sourceReference(),
                r.sourceExcerpt(), r.excerptVerified(), r.humanReviewed(), status, conditional, evidence, explanation,
                action, members);
    }
}
