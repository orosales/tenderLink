package ca.fusion3.tenderready;

import ca.fusion3.tenderready.assessment.CompanyFacts;
import ca.fusion3.tenderready.assessment.Model.AppliesTo;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.Requirement;
import ca.fusion3.tenderready.assessment.Model.RequirementCategory;
import ca.fusion3.tenderready.assessment.Model.RequirementKind;
import ca.fusion3.tenderready.companies.CapacityEntry;
import ca.fusion3.tenderready.companies.Qualification;
import ca.fusion3.tenderready.companies.Qualification.EvidenceStatus;
import java.util.List;
import java.util.UUID;

/** The fictional Halifax 60h / Truro 20h scenario as plain test data. */
public final class Fixtures {
    private Fixtures() {}

    public static final String SERVICE = "commercial office cleaning";

    public static Requirement capacity(String id, String area, int hours) {
        return new Requirement(id, hours + "h in " + area, RequirementCategory.DELIVERY_CONSTRAINT, RequirementKind.CAPACITY,
                AppliesTo.TEAM_COMBINED, area, SERVICE, hours, null, List.of(), "Section 2", "excerpt", true, false, List.of());
    }

    public static Requirement certificationEachCompany() {
        return new Requirement("REQ-03", "CleanSafe for each delivery company", RequirementCategory.MANDATORY_ELIGIBILITY,
                RequirementKind.QUALIFICATION, AppliesTo.EACH_DELIVERY_COMPANY, null, null, null, "CleanSafe Level 2",
                List.of("cleansafe"), "Section 3.1", "excerpt", true, false, List.of());
    }

    public static Extraction extraction(PartnershipPermission permission, Requirement... reqs) {
        return new Extraction(true, null, "scope", List.of(), List.of(reqs), permission, "Subcontracting", "Section 4",
                "excerpt", true, List.of("Buyer must approve named subcontractors"), null, List.of());
    }

    public static Extraction demoExtraction(PartnershipPermission permission) {
        return extraction(permission, capacity("REQ-01", "Halifax", 60), capacity("REQ-02", "Truro", 20), certificationEachCompany());
    }

    public static CompanyFacts harbour() {
        return company(UUID.fromString("0a000000-0000-4000-8000-00000000000a"), "Harbour Clean", true, true,
                List.of(new CapacityEntry("Halifax", "Commercial office cleaning", 60)),
                List.of(new Qualification("CleanSafe Level 2 certification", EvidenceStatus.DECLARED_NO_EVIDENCE, null)));
    }

    public static CompanyFacts fundy(boolean optedIn, boolean confirmed, int truroHours) {
        return company(UUID.fromString("0b000000-0000-4000-8000-00000000000b"), "Fundy Clean", optedIn, confirmed,
                truroHours > 0 ? List.of(new CapacityEntry("Truro", "Commercial office cleaning", truroHours)) : List.of(),
                List.of(new Qualification("CleanSafe Level 2 certification", EvidenceStatus.EVIDENCED, "private note")));
    }

    public static CompanyFacts company(UUID id, String name, boolean optedIn, boolean confirmed, List<CapacityEntry> capacity,
                                       List<Qualification> quals) {
        return new CompanyFacts(id, name, confirmed, optedIn, 1, List.of("Commercial office cleaning"), List.of(),
                capacity.stream().map(CapacityEntry::area).toList(), capacity, null, quals);
    }
}
