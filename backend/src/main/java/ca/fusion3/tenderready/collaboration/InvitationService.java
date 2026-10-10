package ca.fusion3.tenderready.collaboration;

import ca.fusion3.tenderready.assessment.AssessmentRepository;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.common.ApiException;
import ca.fusion3.tenderready.companies.Company;
import ca.fusion3.tenderready.companies.CompanyService;
import ca.fusion3.tenderready.tenders.Tender;
import ca.fusion3.tenderready.tenders.TenderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
    public record CreateInvitationRequest(@NotNull UUID tenderId, @NotNull UUID recipientCompanyId,
                                          @NotNull @Valid ProposedContribution proposedContribution,
                                          @Size(max = 2000) String message) {}

    public record Party(UUID id, String name) {}

    public record InvitationView(UUID id, UUID tenderId, String tenderTitle, boolean tenderDemo, Party sender,
                                 Party recipient, ProposedContribution proposedContribution, String message,
                                 InvitationState state, Instant senderConfirmedAt, Instant recipientConfirmedAt,
                                 Instant respondedAt, Instant createdAt, Instant updatedAt, String viewerRole,
                                 List<String> availableActions, UUID latestCombinedAssessmentId) {}

    private final InvitationRepository invitations;
    private final CompanyService companies;
    private final TenderService tenders;
    private final AssessmentRepository assessments;

    public InvitationService(InvitationRepository invitations, CompanyService companies, TenderService tenders,
                             AssessmentRepository assessments) {
        this.invitations = invitations;
        this.companies = companies;
        this.tenders = tenders;
        this.assessments = assessments;
    }

    /** Only reached through an explicit human "Approve and invite" action; analysis never calls this. */
    @Transactional
    public InvitationView create(Company active, CreateInvitationRequest req) {
        Tender tender = tenders.get(req.tenderId());
        if (active.getId().equals(req.recipientCompanyId())) {
            throw ApiException.unprocessable("SELF_INVITATION", "A company cannot invite itself.");
        }
        Company recipient = companies.get(req.recipientCompanyId());
        if (!recipient.isOpenToPartnerships()) {
            throw ApiException.unprocessable("NOT_OPEN", recipient.getName() + " is not open to partnerships.");
        }
        if (!recipient.isConfirmed()) {
            throw ApiException.unprocessable("UNCONFIRMED", recipient.getName() + " has no confirmed profile.");
        }
        assessments.findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(tender.getId(), active.getId())
                .filter(a -> a.getPartnershipPermission() == PartnershipPermission.PROHIBITED)
                .ifPresent(a -> {
                    throw ApiException.unprocessable("PROHIBITED", "The tender document prohibits this partnering arrangement.");
                });
        boolean duplicate = invitations.existsByTenderIdAndSenderCompanyIdAndRecipientCompanyIdAndStateIn(
                tender.getId(), active.getId(), recipient.getId(), InvitationState.ACTIVE)
                || invitations.existsByTenderIdAndSenderCompanyIdAndRecipientCompanyIdAndStateIn(
                tender.getId(), recipient.getId(), active.getId(), InvitationState.ACTIVE);
        if (duplicate) {
            throw ApiException.conflict("DUPLICATE_INVITATION",
                    "An active invitation already exists between these companies for this tender.");
        }
        Invitation inv = invitations.save(new Invitation(tender.getId(), active.getId(), recipient.getId(),
                req.proposedContribution(), req.message()));
        return view(inv, active.getId());
    }

    @Transactional(readOnly = true)
    public List<InvitationView> list(Company active) {
        return invitations.findInvolving(active.getId()).stream().map(i -> view(i, active.getId())).toList();
    }

    @Transactional(readOnly = true)
    public InvitationView get(Company active, UUID id) {
        return view(require(active, id), active.getId());
    }

    @Transactional
    public InvitationView respond(Company active, UUID id, InvitationRules.Response response) {
        Invitation inv = require(active, id);
        InvitationRules.respond(inv, active.getId(), response);
        return view(inv, active.getId());
    }

    @Transactional
    public InvitationView confirm(Company active, UUID id) {
        Invitation inv = require(active, id);
        InvitationRules.confirmContribution(inv, active.getId());
        return view(inv, active.getId());
    }

    @Transactional
    public InvitationView withdraw(Company active, UUID id) {
        Invitation inv = require(active, id);
        InvitationRules.withdraw(inv, active.getId());
        return view(inv, active.getId());
    }

    public Invitation require(Company active, UUID id) {
        Invitation inv = invitations.findById(id).orElseThrow(() -> ApiException.notFound("Invitation"));
        if (!inv.involves(active.getId())) {
            throw ApiException.forbidden("This invitation does not involve the active company.");
        }
        return inv;
    }

    InvitationView view(Invitation i, UUID viewer) {
        Tender t = tenders.get(i.getTenderId());
        Company s = companies.get(i.getSenderCompanyId());
        Company r = companies.get(i.getRecipientCompanyId());
        boolean isSender = i.getSenderCompanyId().equals(viewer);
        List<String> actions = new ArrayList<>();
        switch (i.getState()) {
            case INVITED -> {
                if (isSender) actions.add("WITHDRAW");
                else { actions.add("ACCEPT_INTEREST"); actions.add("DECLINE"); }
            }
            case INTEREST_ACCEPTED -> {
                if ((isSender && i.getSenderConfirmedAt() == null) || (!isSender && i.getRecipientConfirmedAt() == null)) {
                    actions.add("CONFIRM_CONTRIBUTION");
                }
                if (isSender) actions.add("WITHDRAW");
            }
            case CONFIRMED_FOR_ASSESSMENT -> {
                actions.add("REASSESS");
                if (isSender) actions.add("WITHDRAW");
            }
            default -> { }
        }
        UUID combined = assessments.findFirstByInvitationIdOrderByCreatedAtDesc(i.getId()).map(a -> a.getId()).orElse(null);
        return new InvitationView(i.getId(), t.getId(), t.getTitle(), t.isDemo(), new Party(s.getId(), s.getName()),
                new Party(r.getId(), r.getName()), i.getProposedContribution(), i.getMessage(), i.getState(),
                i.getSenderConfirmedAt(), i.getRecipientConfirmedAt(), i.getRespondedAt(), i.getCreatedAt(),
                i.getUpdatedAt(), isSender ? "SENDER" : "RECIPIENT", actions, combined);
    }
}
