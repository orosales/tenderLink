package ca.fusion3.tenderready.assessment;

import ca.fusion3.tenderready.common.Text;
import ca.fusion3.tenderready.companies.CapacityEntry;
import ca.fusion3.tenderready.companies.Qualification;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic comparisons between requirement fields and declared profile data. */
public final class ProfileMatching {
    private ProfileMatching() {}

    public static boolean areaMatches(String required, String declared) {
        return Text.sameKey(required, declared);
    }

    /** Services match on containment or majority word overlap ("commercial cleaning" ~ "commercial office cleaning"). */
    public static boolean serviceMatches(String required, String declared) {
        String r = Text.norm(required);
        String d = Text.norm(declared);
        if (r.isEmpty()) return true;
        if (d.isEmpty()) return false;
        if (r.contains(d) || d.contains(r)) return true;
        Set<String> rw = words(r);
        Set<String> dw = words(d);
        long common = rw.stream().filter(dw::contains).count();
        return common > 0 && common * 2 >= Math.min(rw.size(), dw.size()) + 1;
    }

    private static Set<String> words(String s) {
        return Arrays.stream(s.split("[^a-z0-9]+")).filter(w -> w.length() > 2).collect(Collectors.toSet());
    }

    /** Declared hours for a service in exactly this area. Hours in other areas never count. */
    public static int hoursIn(List<CapacityEntry> capacity, String area, String service) {
        if (capacity == null) return 0;
        return capacity.stream()
                .filter(e -> areaMatches(area, e.area()) && serviceMatches(service, e.service()))
                .mapToInt(e -> e.weeklyHours() == null ? 0 : e.weeklyHours())
                .sum();
    }

    public static Optional<Qualification> findQualification(List<Qualification> quals, String name, List<String> terms) {
        if (quals == null) return Optional.empty();
        String n = Text.norm(name);
        return quals.stream().filter(q -> {
            String qn = Text.norm(q.name());
            if (!n.isEmpty() && (qn.contains(n) || n.contains(qn))) return true;
            return terms != null && terms.stream().map(Text::norm).anyMatch(t -> t.length() > 2 && qn.contains(t));
        }).findFirst();
    }

    /** Finds the first profile field containing one of the terms; returns "Field: value" or empty. */
    public static Optional<String> findSupportingText(CompanyFacts c, List<String> terms) {
        if (terms == null || terms.isEmpty()) return Optional.empty();
        record Field(String label, String value) {}
        List<Field> fields = new java.util.ArrayList<>();
        c.services().forEach(s -> fields.add(new Field("Services", s)));
        c.specializations().forEach(s -> fields.add(new Field("Specializations", s)));
        if (c.experience() != null) fields.add(new Field("Experience", c.experience()));
        c.qualifications().forEach(q -> fields.add(new Field("Qualifications", q.name())));
        for (String term : terms) {
            String t = Text.norm(term);
            if (t.length() < 3) continue;
            for (Field f : fields) {
                if (Text.norm(f.value()).contains(t)) return Optional.of(f.label() + ": " + f.value());
            }
        }
        return Optional.empty();
    }
}
