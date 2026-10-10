package ca.fusion3.tenderready.tenders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tender_document")
public class TenderDocument {
    public enum AccessStatus { AVAILABLE, UNAVAILABLE, EXTRACTION_LIMITED }

    @Id
    private UUID id;
    private UUID tenderId;
    private String name;
    private String url;
    private String extractedText;
    private String versionHash;
    @Enumerated(EnumType.STRING)
    private AccessStatus accessStatus;
    private String accessNote;
    @Column(name = "is_demo")
    private boolean demo;
    private Instant fetchedAt = Instant.now();

    protected TenderDocument() {}

    public TenderDocument(UUID id, UUID tenderId, String name, String url) {
        this.id = id;
        this.tenderId = tenderId;
        this.name = name;
        this.url = url;
    }

    public UUID getId() { return id; }
    public UUID getTenderId() { return tenderId; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String v) { this.extractedText = v; }
    public String getVersionHash() { return versionHash; }
    public void setVersionHash(String v) { this.versionHash = v; }
    public AccessStatus getAccessStatus() { return accessStatus; }
    public void setAccessStatus(AccessStatus v) { this.accessStatus = v; }
    public String getAccessNote() { return accessNote; }
    public void setAccessNote(String v) { this.accessNote = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant v) { this.fetchedAt = v; }
}
