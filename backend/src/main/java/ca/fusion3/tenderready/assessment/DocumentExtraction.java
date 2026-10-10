package ca.fusion3.tenderready.assessment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cached, validated interpretation of one document version (company-independent). */
@Entity
@Table(name = "document_extraction")
public class DocumentExtraction {
    @Id
    private UUID id;
    private UUID tenderId;
    private String documentVersion;
    @Enumerated(EnumType.STRING)
    private Model.Mode mode;
    private String model;
    @JdbcTypeCode(SqlTypes.JSON)
    private Model.Extraction result;
    private Instant createdAt = Instant.now();

    protected DocumentExtraction() {}

    public DocumentExtraction(UUID tenderId, String documentVersion, Model.Mode mode, String model, Model.Extraction result) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.documentVersion = documentVersion;
        this.mode = mode;
        this.model = model;
        this.result = result;
    }

    public UUID getId() { return id; }
    public Model.Mode getMode() { return mode; }
    public String getModel() { return model; }
    public Model.Extraction getResult() { return result; }
}
