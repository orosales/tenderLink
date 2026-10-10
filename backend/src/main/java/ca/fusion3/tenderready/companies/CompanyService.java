package ca.fusion3.tenderready.companies;

import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.common.Text;
import ca.fusion3.tenderready.companies.CompanyDtos.UpdateCompanyRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {
    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    public Company get(UUID id) {
        return companies.findById(id).orElseThrow(() -> ApiException.notFound("Company"));
    }

    @Transactional
    public Company update(Company active, UUID id, UpdateCompanyRequest req) {
        if (!active.getId().equals(id)) {
            throw ApiException.forbidden("A company can only edit its own profile.");
        }
        Company c = get(id);
        List<CapacityEntry> capacity = clean(req.capacity());
        List<String> areas = cleanStrings(req.serviceAreas());
        for (CapacityEntry e : capacity) {
            // Capacity must belong to a declared service area so hours are never assumed usable elsewhere.
            if (areas.stream().noneMatch(a -> Text.sameKey(a, e.area()))) {
                areas.add(e.area().trim());
            }
        }
        boolean changed = !Objects.equals(c.getName(), req.name().trim())
                || !Objects.equals(c.getContactName(), blankToNull(req.contactName()))
                || !c.getServices().equals(cleanStrings(req.services()))
                || !c.getSpecializations().equals(cleanStrings(req.specializations()))
                || !c.getServiceAreas().equals(areas)
                || !c.getCapacity().equals(capacity)
                || !Objects.equals(c.getExperience(), blankToNull(req.experience()))
                || !c.getQualifications().equals(cleanQualifications(req.qualifications()))
                || !Objects.equals(c.getEvidenceSummary(), blankToNull(req.evidenceSummary()))
                || !Objects.equals(c.getPartnershipPreference(), blankToNull(req.partnershipPreference()))
                || c.isOpenToPartnerships() != req.openToPartnerships();
        if (!changed) return c;

        c.setName(req.name().trim());
        c.setContactName(blankToNull(req.contactName()));
        c.setServices(cleanStrings(req.services()));
        c.setSpecializations(cleanStrings(req.specializations()));
        c.setServiceAreas(areas);
        c.setCapacity(capacity);
        c.setExperience(blankToNull(req.experience()));
        c.setQualifications(cleanQualifications(req.qualifications()));
        c.setEvidenceSummary(blankToNull(req.evidenceSummary()));
        c.setPartnershipPreference(blankToNull(req.partnershipPreference()));
        c.setOpenToPartnerships(req.openToPartnerships());
        // Any edit is a new revision and needs a fresh human confirmation.
        c.setProfileRevision(c.getProfileRevision() + 1);
        c.setProfileConfirmedAt(null);
        c.setUpdatedAt(Instant.now());
        return c;
    }

    @Transactional
    public Company confirm(Company active, UUID id) {
        if (!active.getId().equals(id)) {
            throw ApiException.forbidden("A company can only confirm its own profile.");
        }
        Company c = get(id);
        c.setProfileConfirmedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }

    private static String blankToNull(String s) {
        return Text.isBlank(s) ? null : s.trim();
    }

    private static List<String> cleanStrings(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) {
            if (!Text.isBlank(s) && out.stream().noneMatch(o -> Text.sameKey(o, s))) out.add(s.trim());
        }
        return out;
    }

    private static List<CapacityEntry> clean(List<CapacityEntry> in) {
        if (in == null) return new ArrayList<>();
        return new ArrayList<>(in.stream()
                .map(e -> new CapacityEntry(e.area().trim(), e.service().trim(), e.weeklyHours()))
                .toList());
    }

    private static List<Qualification> cleanQualifications(List<Qualification> in) {
        if (in == null) return new ArrayList<>();
        return new ArrayList<>(in.stream()
                .map(q -> new Qualification(q.name().trim(), q.status(), blankToNull(q.evidenceNote())))
                .toList());
    }
}
