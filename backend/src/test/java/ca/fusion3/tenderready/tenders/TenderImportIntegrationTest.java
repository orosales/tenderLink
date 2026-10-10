package ca.fusion3.tenderready.tenders;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs against a throwaway PostgreSQL container, never the configured database. */
@Testcontainers
@SpringBootTest(properties = {"DB_URL=jdbc:postgresql://unused", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "DB_SCHEMA=tender_test", "AI_MODE=fixture", "ANTHROPIC_API_KEY="})
class TenderImportIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired TenderImportService importer;
    @Autowired TenderRepository tenders;

    @Test
    void repeatedImportsDoNotDuplicateAndAbsentNoticesAreKept() throws Exception {
        String twoRows = CanadaBuysCsvParserTest.HEADER
                + CanadaBuysCsvParserTest.row("Cleaning A", "REF-A", "000", "desc a")
                + CanadaBuysCsvParserTest.row("Same title", "REF-B", "000", "desc b")
                + CanadaBuysCsvParserTest.row("Same title", "REF-C", "000", "desc c");

        var first = importer.importFrom(new StringReader(twoRows), "TEST");
        assertThat(first.getInserted()).isEqualTo(3); // identical titles are distinct notices
        var second = importer.importFrom(new StringReader(twoRows), "TEST");
        assertThat(second.getInserted()).isZero();
        assertThat(second.getUnchanged()).isEqualTo(3);
        assertThat(tenders.countBySource(Tender.SOURCE_CANADABUYS)).isEqualTo(3);

        String amendedWithoutC = CanadaBuysCsvParserTest.HEADER
                + CanadaBuysCsvParserTest.row("Cleaning A", "REF-A", "001", "desc a amended")
                + CanadaBuysCsvParserTest.row("Same title", "REF-B", "000", "desc b");
        var third = importer.importFrom(new StringReader(amendedWithoutC), "TEST");
        assertThat(third.getUpdated()).isEqualTo(1);
        assertThat(third.getUnchanged()).isEqualTo(1);
        assertThat(tenders.countBySource(Tender.SOURCE_CANADABUYS)).isEqualTo(3);
        assertThat(tenders.findBySourceAndSourceId(Tender.SOURCE_CANADABUYS, "REF-C")).isPresent();
        assertThat(tenders.findBySourceAndSourceId(Tender.SOURCE_CANADABUYS, "REF-A").orElseThrow().getDescription())
                .isEqualTo("desc a amended");
    }
}
