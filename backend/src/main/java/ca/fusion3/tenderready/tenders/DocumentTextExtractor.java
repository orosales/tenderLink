package ca.fusion3.tenderready.tenders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts text from fetched documents. Page markers allow findings to cite page numbers. */
@Component
public class DocumentTextExtractor {
    private static final int MIN_USEFUL_CHARS = 300;

    public record Extracted(TenderDocument.AccessStatus status, String text, String note) {}

    public Extracted extract(SafeDocumentFetcher.Fetched f) {
        byte[] body = f.body();
        boolean pdf = f.contentType().contains("pdf") || (body.length > 4 && body[0] == '%' && body[1] == 'P' && body[2] == 'D' && body[3] == 'F');
        if (pdf) return pdf(body);
        if (f.contentType().startsWith("text/plain")) {
            String text = new String(body, StandardCharsets.UTF_8);
            return useful(text, "Plain text document");
        }
        if (f.contentType().contains("html")) {
            String html = new String(body, StandardCharsets.UTF_8);
            String lower = html.toLowerCase(Locale.ROOT);
            String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                    .replaceAll("(?s)<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ").replaceAll("&amp;", "&")
                    .replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
            if ((lower.contains("sign in") || lower.contains("log in") || lower.contains("login")) && text.length() < 4000) {
                return new Extracted(TenderDocument.AccessStatus.UNAVAILABLE, null,
                        "The page appears to be behind a sign-in wall. The solicitation document was not retrieved.");
            }
            return useful(text, "HTML page; may be a notice summary rather than the full solicitation");
        }
        return new Extracted(TenderDocument.AccessStatus.UNAVAILABLE, null,
                "Unsupported document type (" + (f.contentType().isBlank() ? "unknown" : f.contentType()) + ")");
    }

    private Extracted pdf(byte[] body) {
        try (PDDocument doc = Loader.loadPDF(body)) {
            if (doc.isEncrypted()) {
                return new Extracted(TenderDocument.AccessStatus.UNAVAILABLE, null, "The PDF is encrypted.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                sb.append("\n[Page ").append(p).append("]\n").append(stripper.getText(doc));
            }
            String text = sb.toString();
            if (text.replaceAll("\\[Page \\d+]|\\s", "").length() < MIN_USEFUL_CHARS) {
                return new Extracted(TenderDocument.AccessStatus.EXTRACTION_LIMITED, null,
                        "The PDF contains little or no extractable text (likely scanned). OCR is outside this prototype.");
            }
            return new Extracted(TenderDocument.AccessStatus.AVAILABLE, text, "PDF, " + doc.getNumberOfPages() + " page(s)");
        } catch (IOException e) {
            return new Extracted(TenderDocument.AccessStatus.UNAVAILABLE, null, "The PDF could not be read: " + e.getMessage());
        }
    }

    private static Extracted useful(String text, String note) {
        if (text.trim().length() < MIN_USEFUL_CHARS) {
            return new Extracted(TenderDocument.AccessStatus.EXTRACTION_LIMITED, null, "Too little text was extracted.");
        }
        return new Extracted(TenderDocument.AccessStatus.AVAILABLE, text, note);
    }
}
