package ca.fusion3.tenderready.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementCorrectionRepository extends JpaRepository<RequirementCorrection, UUID> {
    List<RequirementCorrection> findByTenderIdAndDocumentVersion(UUID tenderId, String documentVersion);

    Optional<RequirementCorrection> findByTenderIdAndDocumentVersionAndRequirementId(UUID tenderId, String documentVersion, String requirementId);
}
