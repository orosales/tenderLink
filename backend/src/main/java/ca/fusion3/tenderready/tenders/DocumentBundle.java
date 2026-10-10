package ca.fusion3.tenderready.tenders;

import java.util.List;
import java.util.UUID;

/**
 * The text an assessment analyses, with an honest statement of whether it is the complete solicitation.
 */
public record DocumentBundle(
        String text,
        String version,
        boolean complete,
        UUID primaryDocumentId,
        List<String> sourceNames,
        List<String> limitations) {}
