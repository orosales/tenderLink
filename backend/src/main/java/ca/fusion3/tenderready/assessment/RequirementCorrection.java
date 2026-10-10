package ca.fusion3.tenderready.assessment;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A human correction to one extracted requirement, kept for reassessment of the same document version. */
@Entity
@Table(name = "requirement_correction")
public class RequirementCorrection {
    /** Only non-null fields override the extracted value. */
    public record Payload(String description, Model.RequirementCategory category, Model.RequirementKind kind,
                          Model.AppliesTo appliesTo, String area, String service, Integer weeklyHours,
                          String qualificationName, java.util.List<String> matchTerms, String reviewerNote) {}

    @Id
    private UUID id;
    private UUID tenderId;
    private String documentVersion;
    private String requirementId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Payload correction;
    private UUID correctedBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected RequirementCorrection() {}

    public RequirementCorrection(UUID tenderId, String documentVersion, String requirementId) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.documentVersion = documentVersion;
        this.requirementId = requirementId;
    }

    public UUID getId() { return id; }
    public UUID getTenderId() { return tenderId; }
    public String getDocumentVersion() { return documentVersion; }
    public String getRequirementId() { return requirementId; }
    public Payload getCorrection() { return correction; }
    public void setCorrection(Payload v) { this.correction = v; }
    public UUID getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(UUID v) { this.correctedBy = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
