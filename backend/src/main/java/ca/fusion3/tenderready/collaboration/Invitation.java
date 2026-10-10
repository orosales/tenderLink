package ca.fusion3.tenderready.collaboration;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invitation")
public class Invitation {
    @Id
    private UUID id;
    private UUID tenderId;
    private UUID senderCompanyId;
    private UUID recipientCompanyId;
    @JdbcTypeCode(SqlTypes.JSON)
    private ProposedContribution proposedContribution;
    private String message;
    @Enumerated(EnumType.STRING)
    private InvitationState state;
    private Instant senderConfirmedAt;
    private Instant recipientConfirmedAt;
    private Instant respondedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Invitation() {}

    public Invitation(UUID tenderId, UUID senderCompanyId, UUID recipientCompanyId, ProposedContribution contribution, String message) {
        this.id = UUID.randomUUID();
        this.tenderId = tenderId;
        this.senderCompanyId = senderCompanyId;
        this.recipientCompanyId = recipientCompanyId;
        this.proposedContribution = contribution;
        this.message = message;
        this.state = InvitationState.INVITED;
    }

    public boolean involves(UUID companyId) {
        return senderCompanyId.equals(companyId) || recipientCompanyId.equals(companyId);
    }

    public UUID getId() { return id; }
    public UUID getTenderId() { return tenderId; }
    public UUID getSenderCompanyId() { return senderCompanyId; }
    public UUID getRecipientCompanyId() { return recipientCompanyId; }
    public ProposedContribution getProposedContribution() { return proposedContribution; }
    public String getMessage() { return message; }
    public InvitationState getState() { return state; }
    void setState(InvitationState v) { this.state = v; this.updatedAt = Instant.now(); }
    public Instant getSenderConfirmedAt() { return senderConfirmedAt; }
    void setSenderConfirmedAt(Instant v) { this.senderConfirmedAt = v; }
    public Instant getRecipientConfirmedAt() { return recipientConfirmedAt; }
    void setRecipientConfirmedAt(Instant v) { this.recipientConfirmedAt = v; }
    public Instant getRespondedAt() { return respondedAt; }
    void setRespondedAt(Instant v) { this.respondedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
