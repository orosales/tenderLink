package ca.fusion3.tenderready.tenders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenderRepository extends JpaRepository<Tender, UUID> {
    Optional<Tender> findBySourceAndSourceId(String source, String sourceId);

    List<Tender> findBySource(String source);

    List<Tender> findByDemoTrue();

    long countBySource(String source);

    @Query("""
            select t from Tender t
            where t.demo = false
              and (:q = '' or lower(t.title) like :q or lower(t.description) like :q or lower(t.category) like :q)
              and (:loc = '' or lower(t.deliveryRegionsRaw) like :loc
                   or (:includeUnknown = true and t.deliveryAreas is null))
            order by case when t.closingAt < current_timestamp then 1 else 0 end, t.closingAt asc nulls last
            """)
    List<Tender> searchLive(@Param("q") String q, @Param("loc") String loc,
                            @Param("includeUnknown") boolean includeUnknown, Pageable page);

    @Query("""
            select count(t) from Tender t
            where t.demo = false
              and (:q = '' or lower(t.title) like :q or lower(t.description) like :q or lower(t.category) like :q)
              and (:loc = '' or lower(t.deliveryRegionsRaw) like :loc
                   or (:includeUnknown = true and t.deliveryAreas is null))
            """)
    long countLive(@Param("q") String q, @Param("loc") String loc, @Param("includeUnknown") boolean includeUnknown);
}
