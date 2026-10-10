package ca.fusion3.tenderready.assessment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assessment")
public class Assessment {
    @Id
    private UUID id;
    private UUID tenderId;
    private UUID companyId;
    private UUID invitationId;
    private UUID baselineAssessmentId;
    private int profileRevision;
    /** Partner company id -> profile revision used, so partner edits invalidate a combined assessment. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Integer> partnerRevisions = new HashMap<>();
    private UUID documentId;
    private String documentVersion;
    @Enumerated(EnumType.STRING)
    private Model.Mode mode;
    private String model;
    private boolean documentComplete;
    @Enumerated(EnumType.STRING)
    private Model.Readiness readiness;
    @JdbcTypeCode(SqlTypes.JSON)
    private Model.Extraction extraction;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Model.Finding> findings = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Model.ActionItem> actions = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> warnings = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private Model.PartnershipPermission partnershipPermission;
    private Instant createdAt = Instant.now();

    protected Assessment() {}

    public Assessment(UUID tenderId, UUID companyId) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.companyId = companyId;
    }

    public UUID getId() { return id; }
    public UUID getTenderId() { return tenderId; }
    public UUID getCompanyId() { return companyId; }
    public UUID getInvitationId() { return invitationId; }
    public void setInvitationId(UUID v) { this.invitationId = v; }
    public UUID getBaselineAssessmentId() { return baselineAssessmentId; }
    public void setBaselineAssessmentId(UUID v) { this.baselineAssessmentId = v; }
    public int getProfileRevision() { return profileRevision; }
    public void setProfileRevision(int v) { this.profileRevision = v; }
    public Map<String, Integer> getPartnerRevisions() { return partnerRevisions; }
    public void setPartnerRevisions(Map<String, Integer> v) { this.partnerRevisions = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { this.documentId = v; }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String v) { this.documentVersion = v; }
    public Model.Mode getMode() { return mode; }
    public void setMode(Model.Mode v) { this.mode = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public boolean isDocumentComplete() { return documentComplete; }
    public void setDocumentComplete(boolean v) { this.documentComplete = v; }
    public Model.Readiness getReadiness() { return readiness; }
    public void setReadiness(Model.Readiness v) { this.readiness = v; }
    public Model.Extraction getExtraction() { return extraction; }
    public void setExtraction(Model.Extraction v) { this.extraction = v; }
    public List<Model.Finding> getFindings() { return findings; }
    public void setFindings(List<Model.Finding> v) { this.findings = v; }
    public List<Model.ActionItem> getActions() { return actions; }
    public void setActions(List<Model.ActionItem> v) { this.actions = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public Model.PartnershipPermission getPartnershipPermission() { return partnershipPermission; }
    public void setPartnershipPermission(Model.PartnershipPermission v) { this.partnershipPermission = v; }
    public Instant getCreatedAt() { return createdAt; }
}
