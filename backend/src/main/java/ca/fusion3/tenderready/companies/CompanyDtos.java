package ca.fusion3.tenderready.companies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CompanyDtos {
    private CompanyDtos() {}

    /** Full profile, only returned to the company itself. */
    public record CompanyView(
            UUID id, String name, String contactName, List<String> services, List<String> specializations,
            List<String> serviceAreas, List<CapacityEntry> capacity, String experience,
            List<Qualification> qualifications, String evidenceSummary, String partnershipPreference,
            boolean openToPartnerships, Instant profileConfirmedAt, int profileRevision, Instant updatedAt,
            boolean demo, boolean ownProfile) {

        static CompanyView of(Company c) {
            return new CompanyView(c.getId(), c.getName(), c.getContactName(), c.getServices(), c.getSpecializations(),
                    c.getServiceAreas(), c.getCapacity(), c.getExperience(), c.getQualifications(),
                    c.getEvidenceSummary(), c.getPartnershipPreference(), c.isOpenToPartnerships(),
                    c.getProfileConfirmedAt(), c.getProfileRevision(), c.getUpdatedAt(), c.isDemo(), true);
        }
    }

    /** What other companies may see: no private evidence notes, qualification status only. */
    public record PublicQualification(String name, Qualification.EvidenceStatus status) {}

    public record CompanyPublicView(
            UUID id, String name, List<String> services, List<String> specializations, List<String> serviceAreas,
            List<CapacityEntry> capacity, List<PublicQualification> qualifications, String partnershipPreference,
            boolean openToPartnerships, boolean confirmed, Instant updatedAt, boolean demo, boolean ownProfile) {

        static CompanyPublicView of(Company c) {
            return new CompanyPublicView(c.getId(), c.getName(), c.getServices(), c.getSpecializations(),
                    c.getServiceAreas(), c.getCapacity(),
                    c.getQualifications().stream().map(q -> new PublicQualification(q.name(), q.status())).toList(),
                    c.getPartnershipPreference(), c.isOpenToPartnerships(), c.isConfirmed(), c.getUpdatedAt(),
                    c.isDemo(), false);
        }
    }

    public record CompanySummary(UUID id, String name, boolean openToPartnerships, boolean confirmed, boolean demo,
                                 List<String> serviceAreas) {}

    public record UpdateCompanyRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String contactName,
            List<@NotBlank String> services,
            List<@NotBlank String> specializations,
            List<@NotBlank String> serviceAreas,
            List<@Valid CapacityEntry> capacity,
            @Size(max = 4000) String experience,
            List<@Valid Qualification> qualifications,
            @Size(max = 4000) String evidenceSummary,
            @Size(max = 2000) String partnershipPreference,
            boolean openToPartnerships) {}
}
