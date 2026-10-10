package ca.fusion3.tenderready.tenders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CanadaBuysCsvParserTest {
    static final String HEADER = "﻿\"title-titre-eng\",\"referenceNumber-numeroReference\",\"amendmentNumber-numeroModification\","
            + "\"tenderClosingDate-appelOffresDateCloture\",\"regionsOfDelivery-regionsLivraison-eng\","
            + "\"contractingEntityName-nomEntitContractante-eng\",\"contractingEntityAddressCity-entiteContractanteAdresseVille-eng\","
            + "\"noticeURL-URLavis-eng\",\"attachment-piecesJointes-eng\",\"tenderDescription-descriptionAppelOffres-eng\"\n";

    static String row(String title, String ref, String amendment, String description) {
        return "\"" + title + "\",\"" + ref + "\",\"" + amendment + "\",\"2026-11-01T14:00:00\",\"*Nova Scotia\n*Ontario (except NCR)\","
                + "\"PWGSC\",\"Gatineau\",\"https://example.org/n\",\"https://a.example/1.pdf,https://a.example/2.pdf\",\"" + description + "\"\n";
    }

    @Test
    void parsesByHeaderWithBomAndMultilineQuotedFields() throws IOException {
        String csv = HEADER + row("Janitorial, \"\"Halifax\"\"", "R-1", "000", "Line one\nLine two, with comma")
                + "\"No delivery\",\"R-2\",\"000\",\"\",\"\",\"Buyer\",\"Ottawa\",\"\",\"\",\"\"\n";
        var result = new CanadaBuysCsvParser().parse(new StringReader(csv));
        assertThat(result.rowsRead()).isEqualTo(2);
        var first = result.notices().get(0);
        assertThat(first.title()).isEqualTo("Janitorial, \"Halifax\"");
        assertThat(first.description()).isEqualTo("Line one\nLine two, with comma");
        assertThat(first.deliveryAreas()).containsExactly("Nova Scotia", "Ontario (except NCR)");
        assertThat(first.buyerAddress()).isEqualTo("Gatineau"); // buyer office, not the work location
        assertThat(first.attachments()).hasSize(2);
        // Closing times are fixed UTC-05:00 per the data dictionary.
        assertThat(first.closingAt()).isEqualTo(Instant.parse("2026-11-01T19:00:00Z"));
        assertThat(result.notices().get(1).deliveryAreas()).isNull(); // unknown, not excluded
    }

    @Test
    void missingColumnsAreReportedNotGuessed() {
        assertThatThrownBy(() -> new CanadaBuysCsvParser().parse(new StringReader("\"title\",\"id\"\n\"a\",\"b\"\n")))
                .hasMessageContaining("missing expected column");
    }
}
