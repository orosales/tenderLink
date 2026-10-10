package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.companies.CapacityEntry;
import ca.fusion3.tenderready.companies.Company;
import ca.fusion3.tenderready.companies.Qualification;
import java.util.List;
import java.util.UUID;

/** The consented profile fields an assessment may use. Excludes contact details and private evidence notes. */
public record CompanyFacts(
        UUID id, String name, boolean confirmed, boolean openToPartnerships, int profileRevision,
        List<String> services, List<String> specializations, List<String> serviceAreas,
        List<CapacityEntry> capacity, String experience, List<Qualification> qualifications) {

    public static CompanyFacts of(Company c) {
        return new CompanyFacts(c.getId(), c.getName(), c.isConfirmed(), c.isOpenToPartnerships(),
                c.getProfileRevision(), c.getServices(), c.getSpecializations(), c.getServiceAreas(),
                c.getCapacity(), c.getExperience(), c.getQualifications());
    }
}
