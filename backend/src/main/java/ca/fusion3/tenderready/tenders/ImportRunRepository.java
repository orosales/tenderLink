package ca.fusion3.tenderready.tenders;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRun, UUID> {
    Optional<ImportRun> findFirstByOrderByStartedAtDesc();

    Optional<ImportRun> findFirstByStatusOrderByStartedAtDesc(String status);
}
