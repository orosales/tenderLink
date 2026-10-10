package ca.fusion3.tenderready.tenders;

import ca.fusion3.tenderready.common.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Downloads public tender documents. Only http(s) URLs whose host resolves to public addresses are allowed,
 * redirects are followed manually and re-validated, no credentials or cookies are ever sent, and both time
 * and size are bounded.
 */
@Component
public class SafeDocumentFetcher {
    private static final int MAX_REDIRECTS = 4;

    public record Fetched(URI finalUri, String contentType, byte[] body) {}

    public static class BlockedUrlException extends IOException {
        public BlockedUrlException(String message) { super(message); }
    }

    private final AppProperties props;
    private final HttpClient client;

    public SafeDocumentFetcher(AppProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Fetched fetch(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url.trim());
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            validate(uri);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(props.documents().timeoutSeconds()))
                    .header("User-Agent", "TenderReady-hackathon-prototype/0.1")
                    .GET().build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                resp.body().close();
                Optional<String> location = resp.headers().firstValue("Location");
                if (location.isEmpty()) throw new IOException("Redirect without a Location header");
                uri = uri.resolve(location.get());
                continue;
            }
            if (code == 401 || code == 403) {
                resp.body().close();
                throw new IOException("The document requires sign-in or is not publicly accessible (HTTP " + code + ")");
            }
            if (code != 200) {
                resp.body().close();
                throw new IOException("Document request returned HTTP " + code);
            }
            try (InputStream in = new BoundedInputStream(resp.body(), props.documents().maxBytes())) {
                byte[] body = in.readAllBytes();
                String type = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                return new Fetched(uri, type, body);
            }
        }
        throw new IOException("Too many redirects");
    }

    static void validate(URI uri) throws BlockedUrlException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new BlockedUrlException("Only http(s) document URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new BlockedUrlException("Document URLs with embedded credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BlockedUrlException("Document URL has no host");
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local") || lower.endsWith(".internal")) {
            throw new BlockedUrlException("Local network destinations are blocked");
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (!isPublic(a)) throw new BlockedUrlException("Private or local network destinations are blocked");
            }
        } catch (UnknownHostException e) {
            throw new BlockedUrlException("Document host could not be resolved");
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false; // fc00::/7 unique local
            boolean v4Mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) v4Mapped = false;
            if (v4Mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            return true;
        }
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        if (first == 0) return false;
        if (first == 100 && second >= 64 && second <= 127) return false; // carrier-grade NAT
        if (first == 198 && (second == 18 || second == 19)) return false; // benchmarking
        return first < 224;
    }
}
