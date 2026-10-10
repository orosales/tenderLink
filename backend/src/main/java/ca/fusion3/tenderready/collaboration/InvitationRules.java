package ca.fusion3.tenderready.collaboration;

import ca.fusion3.tenderready.common.ApiException;
import java.time.Instant;
import java.util.UUID;

/**
 * State transitions and per-action authorization for invitations. Pure logic; the service persists.
 * INVITED -> INTEREST_ACCEPTED | DECLINED (recipient only)
 * INTEREST_ACCEPTED -> CONFIRMED_FOR_ASSESSMENT once sender AND recipient have each confirmed the contribution
 * INVITED | INTEREST_ACCEPTED | CONFIRMED_FOR_ASSESSMENT -> WITHDRAWN (sender only)
 */
public final class InvitationRules {
    private InvitationRules() {}

    public enum Response { ACCEPT_INTEREST, DECLINE }

    public static void respond(Invitation inv, UUID actor, Response response) {
        if (!inv.getRecipientCompanyId().equals(actor)) {
            throw ApiException.forbidden("Only the invited company can respond to this invitation.");
        }
        if (inv.getState() != InvitationState.INVITED) {
            throw invalid(inv, "respond");
        }
        inv.setState(response == Response.ACCEPT_INTEREST ? InvitationState.INTEREST_ACCEPTED : InvitationState.DECLINED);
        inv.setRespondedAt(Instant.now());
    }

    public static void confirmContribution(Invitation inv, UUID actor) {
        if (!inv.involves(actor)) {
            throw ApiException.forbidden("Only the two companies in this invitation can confirm it.");
        }
        if (inv.getState() != InvitationState.INTEREST_ACCEPTED) {
            throw invalid(inv, "confirm the proposed contribution");
        }
        Instant now = Instant.now();
        if (inv.getSenderCompanyId().equals(actor)) {
            if (inv.getSenderConfirmedAt() != null) throw ApiException.conflict("ALREADY_CONFIRMED", "You have already confirmed this proposal.");
            inv.setSenderConfirmedAt(now);
        } else {
            if (inv.getRecipientConfirmedAt() != null) throw ApiException.conflict("ALREADY_CONFIRMED", "You have already confirmed this proposal.");
            inv.setRecipientConfirmedAt(now);
        }
        if (inv.getSenderConfirmedAt() != null && inv.getRecipientConfirmedAt() != null) {
            inv.setState(InvitationState.CONFIRMED_FOR_ASSESSMENT);
        } else {
            inv.setState(InvitationState.INTEREST_ACCEPTED);
        }
    }

    public static void withdraw(Invitation inv, UUID actor) {
        if (!inv.getSenderCompanyId().equals(actor)) {
            throw ApiException.forbidden("Only the company that sent the invitation can withdraw it.");
        }
        if (!inv.getState().isActive()) {
            throw invalid(inv, "withdraw");
        }
        inv.setState(InvitationState.WITHDRAWN);
    }

    private static ApiException invalid(Invitation inv, String action) {
        return ApiException.conflict("INVALID_TRANSITION",
                "Cannot " + action + " while the invitation is " + inv.getState() + ".");
    }
}
