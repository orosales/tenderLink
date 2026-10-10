package ca.fusion3.tenderready.tenders;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderDocumentRepository extends JpaRepository<TenderDocument, UUID> {
    List<TenderDocument> findByTenderIdOrderByNameAsc(UUID tenderId);

    void deleteByTenderId(UUID tenderId);
}
