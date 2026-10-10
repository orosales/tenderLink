package ca.fusion3.tenderready.collaboration;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    @Query("select i from Invitation i where i.senderCompanyId = :c or i.recipientCompanyId = :c order by i.updatedAt desc")
    List<Invitation> findInvolving(@Param("c") UUID companyId);

    boolean existsByTenderIdAndSenderCompanyIdAndRecipientCompanyIdAndStateIn(
            UUID tenderId, UUID sender, UUID recipient, Collection<InvitationState> states);

    List<Invitation> findByTenderIdAndSenderCompanyId(UUID tenderId, UUID sender);
}
