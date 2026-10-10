package ca.fusion3.tenderready.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import ca.fusion3.tenderready.assessment.Model.AiExtraction;
import ca.fusion3.tenderready.assessment.Model.AiRequirement;
import ca.fusion3.tenderready.assessment.Model.AppliesTo;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.RequirementCategory;
import ca.fusion3.tenderready.assessment.Model.RequirementKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionValidatorTest {
    private static final String DOC = "2.2 Truro: The contractor must provide 20 labour hours weekly in Truro.\n"
            + "4.1 Subcontracting is permitted only with buyer approval.";

    private static AiRequirement req(String excerpt, int hours) {
        return new AiRequirement("REQ-01", "Truro hours", RequirementCategory.DELIVERY_CONSTRAINT, RequirementKind.CAPACITY,
                AppliesTo.TEAM_COMBINED, "Truro", "cleaning", hours, "", List.of(), "2.2", excerpt);
    }

    private static AiExtraction ai(AiRequirement r, PartnershipPermission p, String permissionExcerpt) {
        return new AiExtraction(true, "", "", List.of(), List.of(r), p, "Subcontracting", "4.1", permissionExcerpt,
                List.of(), "", List.of());
    }

    @Test
    void inventedHoursAndUnsupportedExcerptsAreRejected() {
        var ok = new ExtractionValidator().validate(ai(req("must provide 20 labour hours weekly in Truro", 20),
                PartnershipPermission.PERMITTED_WITH_CONDITIONS, "Subcontracting is permitted only with buyer approval."), DOC, true);
        assertThat(ok.requirements().get(0).weeklyHours()).isEqualTo(20);
        assertThat(ok.requirements().get(0).excerptVerified()).isTrue();

        var invented = new ExtractionValidator().validate(ai(req("must provide 20 labour hours weekly in Truro", 40),
                PartnershipPermission.PERMITTED_WITH_CONDITIONS, "not in the document at all"), DOC, true);
        assertThat(invented.requirements().get(0).weeklyHours()).isNull();
        assertThat(invented.partnershipPermission()).isEqualTo(PartnershipPermission.UNKNOWN);

        var paraphrased = new ExtractionValidator().validate(ai(req("Twenty hours are needed in Truro", 20),
                PartnershipPermission.UNKNOWN, ""), DOC, false);
        assertThat(paraphrased.requirements().get(0).excerptVerified()).isFalse();
        assertThat(paraphrased.requirements().get(0).weeklyHours()).isNull();
        assertThat(paraphrased.documentComplete()).isFalse();
    }
}
