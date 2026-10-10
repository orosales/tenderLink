package ca.fusion3.tenderready.collaboration;

import java.util.EnumSet;
import java.util.Set;

public enum InvitationState {
    INVITED, INTEREST_ACCEPTED, DECLINED, CONFIRMED_FOR_ASSESSMENT, WITHDRAWN;

    public static final Set<InvitationState> ACTIVE = EnumSet.of(INVITED, INTEREST_ACCEPTED, CONFIRMED_FOR_ASSESSMENT);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
