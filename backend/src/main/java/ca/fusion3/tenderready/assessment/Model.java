package ca.fusion3.tenderready.assessment;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Shared value types for requirement extraction and assessment findings (persisted as JSONB). */
public final class Model {
    private Model() {}

    public enum FindingStatus { MATCH, GAP, MISSING_EVIDENCE, UNKNOWN }

    public enum RequirementCategory { MANDATORY_ELIGIBILITY, DELIVERY_CONSTRAINT, SCORED_CRITERION }

    public enum RequirementKind { CAPACITY, QUALIFICATION, PARTNERING, OTHER }

    public enum AppliesTo { LEAD_BIDDER, EACH_DELIVERY_COMPANY, TEAM_COMBINED, UNKNOWN }

    public enum PartnershipPermission { PERMITTED_WITH_CONDITIONS, PROHIBITED, UNKNOWN }

    public enum Mode { LIVE, FIXTURE }

    public enum Readiness { INCOMPLETE_ASSESSMENT, GAPS_TO_RESOLVE, CONDITIONS_OUTSTANDING, NO_GAPS_IN_ASSESSED_REQUIREMENTS }

    // ---- AI / fixture output contract (schema-validated) ----

    public record AiDeadline(
            @JsonPropertyDescription("Short label, e.g. 'Bid closing' or 'Questions due'") String label,
            @JsonPropertyDescription("The date or relative timing exactly as stated in the document") String value,
            @JsonPropertyDescription("Section or page reference, e.g. 'Section 6' or 'Page 3'; empty if none") String sourceReference,
            @JsonPropertyDescription("Verbatim quote from the document supporting this deadline") String sourceExcerpt) {}

    public record AiRequirement(
            @JsonPropertyDescription("Stable id: REQ-01, REQ-02, ...") String id,
            @JsonPropertyDescription("One-sentence plain-language requirement") String description,
            RequirementCategory category,
            @JsonPropertyDescription("CAPACITY for explicit labour hours at a location; QUALIFICATION for a certification, licence, insurance or credential; PARTNERING for subcontracting / joint bid rules; OTHER for anything else") RequirementKind kind,
            @JsonPropertyDescription("Who must satisfy it. TEAM_COMBINED only for delivery capacity at a location; UNKNOWN if the document does not say") AppliesTo appliesTo,
            @JsonPropertyDescription("Delivery location (city or site) for CAPACITY requirements; empty string otherwise") String area,
            @JsonPropertyDescription("Service type for CAPACITY requirements, e.g. 'commercial office cleaning'; empty string otherwise") String service,
            @JsonPropertyDescription("Weekly labour hours explicitly stated in the excerpt; 0 if no number is stated. Never estimate.") int weeklyHours,
            @JsonPropertyDescription("Name of the required qualification for QUALIFICATION requirements; empty string otherwise") String qualificationName,
            @JsonPropertyDescription("2-4 short lowercase terms a company profile would contain if it met this requirement") List<String> matchTerms,
            @JsonPropertyDescription("Section or page reference; empty if none") String sourceReference,
            @JsonPropertyDescription("Verbatim quote from the document (copy exactly, do not paraphrase)") String sourceExcerpt) {}

    public record AiExtraction(
            @JsonPropertyDescription("True only if the text appears to be the complete solicitation document, not just a notice summary") boolean documentComplete,
            @JsonPropertyDescription("Why the document is or is not considered complete") String documentCompletenessNote,
            @JsonPropertyDescription("Two-sentence summary of the scope of work") String scopeSummary,
            List<AiDeadline> deadlines,
            List<AiRequirement> requirements,
            @JsonPropertyDescription("Whether the proposed arrangement (subcontracting) is permitted. Subcontracting permission is not joint-venture permission.") PartnershipPermission partnershipPermission,
            @JsonPropertyDescription("The arrangement the permission refers to, e.g. 'Subcontracting'") String partnershipArrangement,
            @JsonPropertyDescription("Section or page reference of the partnering clause; empty if none") String partnershipSourceReference,
            @JsonPropertyDescription("Verbatim quote of the partnering clause; empty if the document is silent") String partnershipExcerpt,
            List<String> partnershipConditions,
            @JsonPropertyDescription("What the document says about joint ventures / joint bids, or that it is silent") String jointVentureNote,
            @JsonPropertyDescription("Important things the document does not establish") List<String> unknowns) {}

    // ---- Normalised, validated extraction used by the engine ----

    public record Deadline(String label, String value, String sourceReference, String sourceExcerpt, boolean excerptVerified) {}

    public record Requirement(
            String id, String description, RequirementCategory category, RequirementKind kind, AppliesTo appliesTo,
            String area, String service, Integer weeklyHours, String qualificationName, List<String> matchTerms,
            String sourceReference, String sourceExcerpt, boolean excerptVerified, boolean humanReviewed,
            List<String> validationNotes) {}

    public record Extraction(
            boolean documentComplete, String documentCompletenessNote, String scopeSummary, List<Deadline> deadlines,
            List<Requirement> requirements, PartnershipPermission partnershipPermission, String partnershipArrangement,
            String partnershipSourceReference, String partnershipExcerpt, boolean partnershipExcerptVerified,
            List<String> partnershipConditions, String jointVentureNote, List<String> unknowns) {

        public Extraction withRequirements(List<Requirement> reqs) {
            return new Extraction(documentComplete, documentCompletenessNote, scopeSummary, deadlines, reqs,
                    partnershipPermission, partnershipArrangement, partnershipSourceReference, partnershipExcerpt,
                    partnershipExcerptVerified, partnershipConditions, jointVentureNote, unknowns);
        }
    }

    // ---- Findings ----

    /** How one team member bears on a requirement (shown so contributions are never silently merged). */
    public record MemberEvaluation(String companyId, String companyName, String role, FindingStatus status,
                                   Integer hoursCounted, String note) {}

    public record Finding(
            String requirementId, String description, RequirementCategory category, RequirementKind kind,
            AppliesTo appliesTo, String sourceReference, String sourceExcerpt, boolean excerptVerified,
            boolean humanReviewed, FindingStatus status, boolean conditional, String companyEvidence,
            String explanation, String action, List<MemberEvaluation> members) {}

    public record ActionItem(int priority, String text, String requirementId) {}
}
