package ca.fusion3.tenderready.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ca.fusion3.tenderready.Fixtures;
import ca.fusion3.tenderready.assessment.AssessmentEngine;
import ca.fusion3.tenderready.assessment.CompanyFacts;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerMatcherTest {
    private final PartnerMatcher matcher = new PartnerMatcher();

    private PartnerMatcher.Result run(PartnershipPermission permission, CompanyFacts... others) {
        var ex = Fixtures.demoExtraction(permission);
        var lead = Fixtures.harbour();
        var findings = new AssessmentEngine().evaluate(ex, lead, List.of()).findings();
        return matcher.match(ex, matcher.gaps(ex, findings, lead), lead, List.of(others));
    }

    @Test
    void truroCapacityProducesRecommendationForTheSpecificGap() {
        var r = run(PartnershipPermission.PERMITTED_WITH_CONDITIONS, Fixtures.harbour(), Fixtures.fundy(true, true, 20));
        assertThat(r.candidates()).hasSize(1);
        var c = r.candidates().get(0);
        assertThat(c.companyName()).isEqualTo("Fundy Clean");
        assertThat(c.addresses()).singleElement().satisfies(g -> {
            assertThat(g.requirementId()).isEqualTo("REQ-02");
            assertThat(g.fullyCovers()).isTrue();
        });
    }

    @Test
    void optedOutOrUnconfirmedCompaniesAreNeverCandidates() {
        assertThat(run(PartnershipPermission.PERMITTED_WITH_CONDITIONS, Fixtures.fundy(false, true, 20)).candidates()).isEmpty();
        assertThat(run(PartnershipPermission.PERMITTED_WITH_CONDITIONS, Fixtures.fundy(true, false, 20)).candidates()).isEmpty();
    }

    @Test
    void removingTruroCapacityRemovesTheRecommendation() {
        assertThat(run(PartnershipPermission.PERMITTED_WITH_CONDITIONS, Fixtures.fundy(true, true, 0)).candidates()).isEmpty();
    }

    @Test
    void permissionDrivesLabelsAndFeasibility() {
        assertThat(run(PartnershipPermission.UNKNOWN, Fixtures.fundy(true, true, 20)).candidates().get(0).label())
                .isEqualTo("Potential fit: buyer clarification required");
        var prohibited = run(PartnershipPermission.PROHIBITED, Fixtures.fundy(true, true, 20));
        assertThat(prohibited.feasible()).isFalse();
        assertThat(prohibited.candidates()).isEmpty();
    }
}
