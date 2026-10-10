package ca.fusion3.tenderready.tenders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tender")
public class Tender {
    public static final String SOURCE_CANADABUYS = "CANADABUYS";
    public static final String SOURCE_DEMO = "DEMO";

    @Id
    private UUID id;
    private String source;
    private String sourceId;
    private String amendmentNumber;
    private String solicitationNumber;
    private String title;
    private String buyer;
    /** The buyer's office address. Not the place where the work is delivered. */
    private String buyerAddress;
    private String description;
    private String category;
    private String sourceUrl;
    private String deliveryRegionsRaw;
    /** Where the work is delivered. {@code null} means unknown, never "nowhere". */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> deliveryAreas;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> attachments = new ArrayList<>();
    private LocalDate publishedOn;
    private Instant closingAt;
    private String sourceTimezone;
    private String status;
    private Instant fetchedAt;
    private Instant lastSeenAt;
    private String contentHash;
    @Column(name = "is_demo")
    private boolean demo;

    protected Tender() {}

    public Tender(UUID id, String source, String sourceId) {
        this.id = id;
        this.source = source;
        this.sourceId = sourceId;
    }

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public String getSourceId() { return sourceId; }
    public String getAmendmentNumber() { return amendmentNumber; }
    public void setAmendmentNumber(String v) { this.amendmentNumber = v; }
    public String getSolicitationNumber() { return solicitationNumber; }
    public void setSolicitationNumber(String v) { this.solicitationNumber = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getBuyer() { return buyer; }
    public void setBuyer(String v) { this.buyer = v; }
    public String getBuyerAddress() { return buyerAddress; }
    public void setBuyerAddress(String v) { this.buyerAddress = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getDeliveryRegionsRaw() { return deliveryRegionsRaw; }
    public void setDeliveryRegionsRaw(String v) { this.deliveryRegionsRaw = v; }
    public List<String> getDeliveryAreas() { return deliveryAreas; }
    public void setDeliveryAreas(List<String> v) { this.deliveryAreas = v; }
    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> v) { this.attachments = v; }
    public LocalDate getPublishedOn() { return publishedOn; }
    public void setPublishedOn(LocalDate v) { this.publishedOn = v; }
    public Instant getClosingAt() { return closingAt; }
    public void setClosingAt(Instant v) { this.closingAt = v; }
    public String getSourceTimezone() { return sourceTimezone; }
    public void setSourceTimezone(String v) { this.sourceTimezone = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant v) { this.fetchedAt = v; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant v) { this.lastSeenAt = v; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String v) { this.contentHash = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
}
