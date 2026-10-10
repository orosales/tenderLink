package ca.fusion3.tenderready.tenders;

import ca.fusion3.tenderready.common.Text;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parses the CanadaBuys open tender notice CSV by column header (quoted, multi-line fields supported).
 * Column names come from the published data dictionary (achatscanada-canadabuys-dd.xml).
 */
public class CanadaBuysCsvParser {
    static final String REFERENCE = "referenceNumber-numeroReference";
    static final String AMENDMENT = "amendmentNumber-numeroModification";
    static final String SOLICITATION = "solicitationNumber-numeroSollicitation";
    static final String TITLE = "title-titre-eng";
    static final String PUBLISHED = "publicationDate-datePublication";
    static final String CLOSING = "tenderClosingDate-appelOffresDateCloture";
    static final String STATUS = "tenderStatus-appelOffresStatut-eng";
    static final String GSIN_DESC = "gsinDescription-nibsDescription-eng";
    static final String UNSPSC_DESC = "unspscDescription-eng";
    static final String CATEGORY = "procurementCategory-categorieApprovisionnement";
    static final String DELIVERY = "regionsOfDelivery-regionsLivraison-eng";
    static final String BUYER = "contractingEntityName-nomEntitContractante-eng";
    static final String BUYER_CITY = "contractingEntityAddressCity-entiteContractanteAdresseVille-eng";
    static final String BUYER_PROVINCE = "contractingEntityAddressProvince-entiteContractanteAdresseProvince-eng";
    static final String NOTICE_URL = "noticeURL-URLavis-eng";
    static final String ATTACHMENTS = "attachment-piecesJointes-eng";
    static final String DESCRIPTION = "tenderDescription-descriptionAppelOffres-eng";

    static final Set<String> REQUIRED = Set.of(REFERENCE, TITLE, CLOSING, DELIVERY, BUYER, NOTICE_URL, DESCRIPTION);

    /**
     * Per the data dictionary, closing date/times are expressed in UTC-05:00 (Eastern Standard Time) all year,
     * so they are converted with a fixed offset rather than the America/Toronto zone.
     */
    public static final ZoneOffset SOURCE_OFFSET = ZoneOffset.ofHours(-5);
    public static final String SOURCE_TIMEZONE_LABEL = "UTC-05:00 (fixed, per CanadaBuys data dictionary)";

    public record ParsedNotice(
            String referenceNumber, String amendmentNumber, String solicitationNumber, String title, String buyer,
            String buyerAddress, String description, String category, String noticeUrl, String deliveryRegionsRaw,
            List<String> deliveryAreas, List<String> attachments, LocalDate publishedOn, Instant closingAt,
            String status) {}

    public record ParseResult(List<ParsedNotice> notices, int rowsRead, int skipped) {}

    public ParseResult parse(Reader input) throws IOException {
        PushbackReader reader = new PushbackReader(input, 1);
        int first = reader.read();
        if (first != -1 && first != '﻿') reader.unread(first);

        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
        List<ParsedNotice> out = new ArrayList<>();
        int rows = 0;
        int skipped = 0;
        try (CSVParser parser = CSVParser.builder().setReader(reader).setFormat(format).get()) {
            var missing = REQUIRED.stream().filter(h -> !parser.getHeaderMap().containsKey(h)).sorted().toList();
            if (!missing.isEmpty()) {
                throw new IOException("Feed format changed: missing expected column(s) " + missing);
            }
            for (CSVRecord r : parser) {
                rows++;
                String ref = get(r, REFERENCE);
                if (Text.isBlank(ref) || Text.isBlank(get(r, TITLE))) {
                    skipped++;
                    continue;
                }
                String deliveryRaw = get(r, DELIVERY);
                out.add(new ParsedNotice(
                        ref.trim(),
                        get(r, AMENDMENT),
                        get(r, SOLICITATION),
                        get(r, TITLE).trim(),
                        get(r, BUYER),
                        joinNonBlank(get(r, BUYER_CITY), get(r, BUYER_PROVINCE)),
                        get(r, DESCRIPTION),
                        firstNonBlank(get(r, GSIN_DESC), get(r, UNSPSC_DESC), get(r, CATEGORY)),
                        get(r, NOTICE_URL),
                        deliveryRaw,
                        splitRegions(deliveryRaw),
                        splitList(get(r, ATTACHMENTS)),
                        parseDate(get(r, PUBLISHED)),
                        parseClosing(get(r, CLOSING)),
                        get(r, STATUS)));
            }
        }
        return new ParseResult(out, rows, skipped);
    }

    private static String get(CSVRecord r, String header) {
        if (!r.isMapped(header) || !r.isSet(header)) return null;
        String v = r.get(header);
        return Text.isBlank(v) ? null : v;
    }

    /** Delivery regions arrive as e.g. "*Nova Scotia\n*Ontario (except NCR)". Empty means unknown. */
    static List<String> splitRegions(String raw) {
        if (Text.isBlank(raw)) return null;
        List<String> areas = Arrays.stream(raw.split("[\\n,]"))
                .map(s -> s.replace("*", "").trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return areas.isEmpty() ? null : areas;
    }

    private static List<String> splitList(String raw) {
        if (Text.isBlank(raw)) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(raw.split("[,\\n]")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    static Instant parseClosing(String raw) {
        if (Text.isBlank(raw)) return null;
        try {
            return LocalDateTime.parse(raw.trim()).toInstant(SOURCE_OFFSET);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(raw.trim()).atStartOfDay().toInstant(SOURCE_OFFSET);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static LocalDate parseDate(String raw) {
        if (Text.isBlank(raw)) return null;
        try {
            return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (!Text.isBlank(v)) return v.trim();
        return null;
    }

    private static String joinNonBlank(String... values) {
        String s = String.join(", ", Arrays.stream(values).filter(v -> !Text.isBlank(v)).map(String::trim).toList());
        return s.isEmpty() ? null : s;
    }
}
