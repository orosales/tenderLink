package ca.fusion3.tenderready.ai;

import ca.fusion3.tenderready.assessment.Model.AiExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Bundled "Demonstration analysis" for the fictional tender only. It is bound to the exact SHA-256 of the
 * bundled document so it can never be applied to another tender or an edited document.
 */
@Component
public class FixtureAnalysis {
    public record Fixture(String documentSha256, String tenderSourceId, AiExtraction extraction) {}

    private final Fixture fixture;

    public FixtureAnalysis(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("demo/fixture-extraction.json").getInputStream()) {
            this.fixture = mapper.readValue(in, Fixture.class);
        }
    }

    public Optional<AiExtraction> forDocument(String tenderSourceId, String documentVersion) {
        if (fixture.tenderSourceId().equals(tenderSourceId) && fixture.documentSha256().equals(documentVersion)) {
            return Optional.of(fixture.extraction());
        }
        return Optional.empty();
    }
}
