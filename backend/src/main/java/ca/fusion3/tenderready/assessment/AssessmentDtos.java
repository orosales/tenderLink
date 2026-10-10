package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.assessment.Model.ActionItem;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.FindingStatus;
import ca.fusion3.tenderready.assessment.Model.Mode;
import ca.fusion3.tenderready.assessment.Model.Readiness;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {
    private AssessmentDtos() {}

    public record Ref(UUID id, String name) {}

    public record Comparison(String requirementId, String description, FindingStatus before, boolean beforeConditional,
                             FindingStatus after, boolean afterConditional, boolean changed) {}

    public record AssessmentView(
            UUID id, UUID tenderId, String tenderTitle, boolean tenderDemo, Ref company, List<Ref> partners,
            UUID invitationId, boolean combined, Mode mode, String modeLabel, String model, boolean documentComplete,
            List<String> documentSources, List<String> limitations, String documentVersion, Readiness readiness,
            String readinessLabel, Extraction extraction, List<Finding> findings, List<ActionItem> actions,
            List<String> warnings, Instant createdAt, boolean stale, List<String> staleReasons,
            UUID baselineAssessmentId, List<Comparison> comparison) {}

    public static String readinessLabel(Readiness r) {
        return switch (r) {
            case INCOMPLETE_ASSESSMENT -> "Assessment incomplete: no readiness verdict";
            case GAPS_TO_RESOLVE -> "Gaps to resolve";
            case CONDITIONS_OUTSTANDING -> "Conditions outstanding";
            case NO_GAPS_IN_ASSESSED_REQUIREMENTS -> "No gaps found in assessed requirements (not an eligibility decision)";
        };
    }
}
