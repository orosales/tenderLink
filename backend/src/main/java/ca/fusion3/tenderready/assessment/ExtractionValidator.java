package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.assessment.Model.AiExtraction;
import ca.fusion3.tenderready.assessment.Model.AiRequirement;
import ca.fusion3.tenderready.assessment.Model.AppliesTo;
import ca.fusion3.tenderready.assessment.Model.Deadline;
import ca.fusion3.tenderready.assessment.Model.Extraction;
import ca.fusion3.tenderready.assessment.Model.PartnershipPermission;
import ca.fusion3.tenderready.assessment.Model.Requirement;
import ca.fusion3.tenderready.assessment.Model.RequirementCategory;
import ca.fusion3.tenderready.assessment.Model.RequirementKind;
import ca.fusion3.tenderready.common.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns schema-valid model (or fixture) output into a trusted extraction: excerpts must occur in the supplied
 * text, explicit hours must appear in the quoted excerpt, and completeness can only be claimed if the document
 * bundle itself is complete.
 */
public final class ExtractionValidator {

    public Extraction validate(AiExtraction ai, String documentText, boolean bundleComplete) {
        String haystack = normalize(documentText);
        List<Requirement> reqs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int n = 0;
        for (AiRequirement r : ai.requirements() == null ? List.<AiRequirement>of() : ai.requirements()) {
            n++;
            List<String> notes = new ArrayList<>();
            String id = Text.isBlank(r.id()) || ids.contains(r.id().trim()) ? String.format("REQ-%02d", n) : r.id().trim();
            while (ids.contains(id)) id = id + "b";
            ids.add(id);
            boolean verified = verified(haystack, r.sourceExcerpt());
            if (!verified) notes.add("Quoted excerpt not found in the document text: unsupported.");
            Integer hours = r.weeklyHours() > 0 ? r.weeklyHours() : null;
            if (hours != null && (!verified || !mentionsNumber(r.sourceExcerpt(), hours))) {
                notes.add("Hours (" + hours + ") are not stated in the verified excerpt and were discarded.");
                hours = null;
            }
            if (hours != null && hours > 10000) {
                notes.add("Implausible hours discarded.");
                hours = null;
            }
            reqs.add(new Requirement(id, nz(r.description(), "(no description)"),
                    r.category() == null ? RequirementCategory.MANDATORY_ELIGIBILITY : r.category(),
                    r.kind() == null ? RequirementKind.OTHER : r.kind(),
                    r.appliesTo() == null ? AppliesTo.UNKNOWN : r.appliesTo(),
                    blankToNull(r.area()), blankToNull(r.service()), hours, blankToNull(r.qualificationName()),
                    r.matchTerms() == null ? List.of() : r.matchTerms().stream().filter(t -> !Text.isBlank(t)).toList(),
                    blankToNull(r.sourceReference()), blankToNull(r.sourceExcerpt()), verified, false, notes));
        }
        List<Deadline> deadlines = ai.deadlines() == null ? List.of() : ai.deadlines().stream()
                .map(d -> new Deadline(d.label(), d.value(), blankToNull(d.sourceReference()), blankToNull(d.sourceExcerpt()),
                        verified(haystack, d.sourceExcerpt())))
                .toList();
        boolean permissionVerified = verified(haystack, ai.partnershipExcerpt());
        PartnershipPermission permission = ai.partnershipPermission() == null ? PartnershipPermission.UNKNOWN : ai.partnershipPermission();
        if (permission != PartnershipPermission.UNKNOWN && !permissionVerified) {
            // A permission claim without a verifiable passage is not trusted.
            permission = PartnershipPermission.UNKNOWN;
        }
        boolean complete = bundleComplete && ai.documentComplete();
        String completenessNote = ai.documentCompletenessNote();
        if (!bundleComplete) {
            completenessNote = "Document unavailable / assessment incomplete: the full solicitation was not retrieved."
                    + (Text.isBlank(completenessNote) ? "" : " Model note: " + completenessNote);
        }
        return new Extraction(complete, completenessNote, ai.scopeSummary(), deadlines, reqs, permission,
                blankToNull(ai.partnershipArrangement()), blankToNull(ai.partnershipSourceReference()),
                blankToNull(ai.partnershipExcerpt()), permissionVerified,
                ai.partnershipConditions() == null ? List.of() : ai.partnershipConditions(),
                ai.jointVentureNote(), ai.unknowns() == null ? List.of() : ai.unknowns());
    }

    static boolean verified(String normalizedHaystack, String excerpt) {
        if (Text.isBlank(excerpt)) return false;
        String e = normalize(excerpt);
        return e.length() >= 8 && normalizedHaystack.contains(e);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace(' ', ' ')
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean mentionsNumber(String excerpt, int n) {
        return excerpt != null && Pattern.compile("(?<![\\d.])" + n + "(?![\\d])").matcher(excerpt).find();
    }

    private static String blankToNull(String s) {
        return Text.isBlank(s) ? null : s.trim();
    }

    private static String nz(String s, String fallback) {
        return Text.isBlank(s) ? fallback : s.trim();
    }
}
