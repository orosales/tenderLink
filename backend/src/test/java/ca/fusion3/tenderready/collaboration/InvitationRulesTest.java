package ca.fusion3.tenderready.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.fusion3.tenderready.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InvitationRulesTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Invitation invitation() {
        return new Invitation(UUID.randomUUID(), a, b,
                new ProposedContribution("Truro", "cleaning", 20, "Subcontractor", "Lead", null, List.of("REQ-02")), null);
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(status));
    }

    @Test
    void onlyRecipientCanRespond() {
        Invitation inv = invitation();
        assertStatus(() -> InvitationRules.respond(inv, a, InvitationRules.Response.ACCEPT_INTEREST), HttpStatus.FORBIDDEN);
        assertStatus(() -> InvitationRules.respond(inv, stranger, InvitationRules.Response.DECLINE), HttpStatus.FORBIDDEN);
        InvitationRules.respond(inv, b, InvitationRules.Response.ACCEPT_INTEREST);
        assertThat(inv.getState()).isEqualTo(InvitationState.INTEREST_ACCEPTED);
        assertStatus(() -> InvitationRules.respond(inv, b, InvitationRules.Response.DECLINE), HttpStatus.CONFLICT);
    }

    @Test
    void oneSidedConfirmationDoesNotConfirmCollaboration() {
        Invitation inv = invitation();
        assertStatus(() -> InvitationRules.confirmContribution(inv, a), HttpStatus.CONFLICT); // not yet accepted
        InvitationRules.respond(inv, b, InvitationRules.Response.ACCEPT_INTEREST);
        InvitationRules.confirmContribution(inv, b);
        assertThat(inv.getState()).isEqualTo(InvitationState.INTEREST_ACCEPTED);
        assertStatus(() -> InvitationRules.confirmContribution(inv, b), HttpStatus.CONFLICT); // twice
        assertStatus(() -> InvitationRules.confirmContribution(inv, stranger), HttpStatus.FORBIDDEN);
        InvitationRules.confirmContribution(inv, a);
        assertThat(inv.getState()).isEqualTo(InvitationState.CONFIRMED_FOR_ASSESSMENT);
        assertThat(inv.getSenderConfirmedAt()).isNotNull();
        assertThat(inv.getRecipientConfirmedAt()).isNotNull();
    }

    @Test
    void onlySenderCanWithdrawAndClosedInvitationsStayClosed() {
        Invitation inv = invitation();
        assertStatus(() -> InvitationRules.withdraw(inv, b), HttpStatus.FORBIDDEN);
        InvitationRules.withdraw(inv, a);
        assertThat(inv.getState()).isEqualTo(InvitationState.WITHDRAWN);
        assertStatus(() -> InvitationRules.withdraw(inv, a), HttpStatus.CONFLICT);
        assertStatus(() -> InvitationRules.respond(inv, b, InvitationRules.Response.ACCEPT_INTEREST), HttpStatus.CONFLICT);

        Invitation declined = invitation();
        InvitationRules.respond(declined, b, InvitationRules.Response.DECLINE);
        assertStatus(() -> InvitationRules.confirmContribution(declined, a), HttpStatus.CONFLICT);
    }
}
