package ca.fusion3.tenderready.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import ca.fusion3.tenderready.Fixtures;
import ca.fusion3.tenderready.assessment.Model.Finding;
import ca.fusion3.tenderready.assessment.Model.FindingStatus;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.Readiness;
import ca.fusion3.tenderready.companies.CapacityEntry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssessmentEngineTest {
    private final AssessmentEngine engine = new AssessmentEngine();

    private static Finding find(AssessmentEngine.Result r, String id) {
        return r.findings().stream().filter(f -> f.requirementId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void capacityIsComparedPerLocation() {
        var r = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), Fixtures.harbour(), List.of());
        assertThat(find(r, "REQ-01").status()).isEqualTo(FindingStatus.MATCH);
        assertThat(find(r, "REQ-02").status()).isEqualTo(FindingStatus.GAP);
        assertThat(r.readiness()).isEqualTo(Readiness.GAPS_TO_RESOLVE);
    }

    @Test
    void hoursInAnotherCityNeverCoverALocationGap() {
        // 80 hours in Halifax still leaves Truro uncovered.
        var big = Fixtures.company(UUID.randomUUID(), "Big Halifax", true, true,
                List.of(new CapacityEntry("Halifax", "Commercial office cleaning", 80)), List.of());
        var r = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), big, List.of());
        assertThat(find(r, "REQ-02").status()).isEqualTo(FindingStatus.GAP);
    }

    @Test
    void declaredQualificationWithoutEvidenceIsMissingEvidenceAndAbsentOneIsUnknown() {
        var r = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), Fixtures.harbour(), List.of());
        assertThat(find(r, "REQ-03").status()).isEqualTo(FindingStatus.MISSING_EVIDENCE);

        var noQuals = Fixtures.company(UUID.randomUUID(), "Empty", true, true,
                List.of(new CapacityEntry("Halifax", "Commercial office cleaning", 60)), List.of());
        var r2 = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), noQuals, List.of());
        assertThat(find(r2, "REQ-03").status()).isEqualTo(FindingStatus.UNKNOWN);
    }

    @Test
    void confirmedPartnerContributionCoversTruroButKeepsConditionsOpen() {
        var team = List.of(new PartnerContribution(Fixtures.fundy(true, true, 20), "Truro", "commercial office cleaning", 20, "Subcontractor"));
        var r = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), Fixtures.harbour(), team);
        Finding truro = find(r, "REQ-02");
        assertThat(truro.status()).isEqualTo(FindingStatus.MATCH);
        assertThat(truro.conditional()).isTrue();
        // Lead's missing evidence is not covered by the partner's evidence.
        assertThat(find(r, "REQ-03").status()).isEqualTo(FindingStatus.MISSING_EVIDENCE);
        assertThat(r.readiness()).isEqualTo(Readiness.CONDITIONS_OUTSTANDING);
    }

    @Test
    void partnerCapacityIsCappedAtCurrentDeclaredHoursAndIgnoredWhenProhibited() {
        var reduced = List.of(new PartnerContribution(Fixtures.fundy(true, true, 10), "Truro", "commercial office cleaning", 20, "Sub"));
        var r = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PERMITTED_WITH_CONDITIONS), Fixtures.harbour(), reduced);
        assertThat(find(r, "REQ-02").status()).isEqualTo(FindingStatus.GAP);

        var full = List.of(new PartnerContribution(Fixtures.fundy(true, true, 20), "Truro", "commercial office cleaning", 20, "Sub"));
        var p = engine.evaluate(Fixtures.demoExtraction(PartnershipPermission.PROHIBITED), Fixtures.harbour(), full);
        assertThat(find(p, "REQ-02").status()).isEqualTo(FindingStatus.GAP);
    }

    @Test
    void incompleteDocumentNeverGetsAVerdict() {
        var ex = Fixtures.demoExtraction(PartnershipPermission.UNKNOWN);
        var incomplete = new Model.Extraction(false, null, null, List.of(), ex.requirements(), ex.partnershipPermission(),
                null, null, null, false, List.of(), null, List.of());
        var r = engine.evaluate(incomplete, Fixtures.harbour(), List.of());
        assertThat(r.readiness()).isEqualTo(Readiness.INCOMPLETE_ASSESSMENT);
    }
}
