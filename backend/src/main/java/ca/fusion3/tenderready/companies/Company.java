package ca.fusion3.tenderready.companies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "company")
public class Company {
    @Id
    private UUID id;
    private String name;
    private String contactName;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> services = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> specializations = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> serviceAreas = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<CapacityEntry> capacity = new ArrayList<>();
    private String experience;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Qualification> qualifications = new ArrayList<>();
    private String evidenceSummary;
    private String partnershipPreference;
    private boolean openToPartnerships;
    private Instant profileConfirmedAt;
    private int profileRevision = 1;
    @Column(name = "is_demo")
    private boolean demo;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Company() {}

    public Company(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public boolean isConfirmed() { return profileConfirmedAt != null; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
    public List<String> getSpecializations() { return specializations; }
    public void setSpecializations(List<String> specializations) { this.specializations = specializations; }
    public List<String> getServiceAreas() { return serviceAreas; }
    public void setServiceAreas(List<String> serviceAreas) { this.serviceAreas = serviceAreas; }
    public List<CapacityEntry> getCapacity() { return capacity; }
    public void setCapacity(List<CapacityEntry> capacity) { this.capacity = capacity; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public List<Qualification> getQualifications() { return qualifications; }
    public void setQualifications(List<Qualification> qualifications) { this.qualifications = qualifications; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }
    public String getPartnershipPreference() { return partnershipPreference; }
    public void setPartnershipPreference(String partnershipPreference) { this.partnershipPreference = partnershipPreference; }
    public boolean isOpenToPartnerships() { return openToPartnerships; }
    public void setOpenToPartnerships(boolean openToPartnerships) { this.openToPartnerships = openToPartnerships; }
    public Instant getProfileConfirmedAt() { return profileConfirmedAt; }
    public void setProfileConfirmedAt(Instant profileConfirmedAt) { this.profileConfirmedAt = profileConfirmedAt; }
    public int getProfileRevision() { return profileRevision; }
    public void setProfileRevision(int profileRevision) { this.profileRevision = profileRevision; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
