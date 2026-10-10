package ca.fusion3.tenderready.companies;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A qualification the company declares. {@code evidenceNote} describes private evidence and is never exposed
 * through partner search or other companies' views.
 */
public record Qualification(@NotBlank String name, @NotNull EvidenceStatus status, String evidenceNote) {
    public enum EvidenceStatus { EVIDENCED, DECLARED_NO_EVIDENCE, UNKNOWN }
}
