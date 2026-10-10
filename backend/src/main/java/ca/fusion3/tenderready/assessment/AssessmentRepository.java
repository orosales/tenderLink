package ca.fusion3.tenderready.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findFirstByTenderIdAndCompanyIdAndInvitationIdIsNullOrderByCreatedAtDesc(UUID tenderId, UUID companyId);

    Optional<Assessment> findFirstByInvitationIdOrderByCreatedAtDesc(UUID invitationId);

    List<Assessment> findByTenderIdAndCompanyIdOrderByCreatedAtDesc(UUID tenderId, UUID companyId);
}
