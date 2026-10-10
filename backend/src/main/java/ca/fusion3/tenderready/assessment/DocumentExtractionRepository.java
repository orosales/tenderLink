package ca.fusion3.tenderready.assessment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {
    Optional<DocumentExtraction> findByTenderIdAndDocumentVersionAndMode(UUID tenderId, String documentVersion, Model.Mode mode);
}
