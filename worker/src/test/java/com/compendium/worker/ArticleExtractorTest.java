package com.compendium.worker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A local HttpServer fixture (JDK-provided, no extra dependency) stands in
// for "a real site" so fetchAndExtract's full jsoup-fetch + Readability4J
// pipeline runs for real, rather than mocking jsoup's fluent Connection API.
class ArticleExtractorTest {

    private static HttpServer server;
    private static String baseUrl;
    private final ArticleExtractor extractor = new ArticleExtractor();

    private static final String REAL_ARTICLE_HTML = """
            <html><head><title>Test Article</title></head><body>
            <article>
            <h1>Test Article</h1>
            <p>This is the first paragraph of a fairly long test article that should
            give Readability4J enough text content to extract meaningfully, well past
            the near-empty threshold used to detect pages with nothing real to read.</p>
            <p>A second paragraph adds even more real content, so the extraction
            produces a substantial body of text for the test to assert against.</p>
            </article>
            </body></html>
            """;

    private static final String PAYWALL_HTML = """
            <html><body><p>Subscribe to keep reading.</p></body></html>
            """;

    private static final String SHORT_ERROR_HTML = """
            <html><body><h1>Internal Server Error</h1></body></html>
            """;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        register("/article", 200, "text/html", REAL_ARTICLE_HTML);
        register("/missing", 404, "text/html", "<html><body>Not found</body></html>");
        register("/paywall", 200, "text/html", PAYWALL_HTML);
        register("/wrong-type", 200, "application/json", "{\"hello\":\"world\"}");
        register("/server-error", 500, "text/html", SHORT_ERROR_HTML);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void register(String path, int status, String contentType, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void fetchAndExtract_returnsExtractedContentForARealArticle() throws Exception {
        ExtractedArticle article = extractor.fetchAndExtract(baseUrl + "/article");

        assertThat(article.title()).isEqualTo("Test Article");
        assertThat(article.content()).contains("fairly long test article");
    }

    @Test
    void fetchAndExtract_throwsPermanentFor404() {
        assertThatThrownBy(() -> extractor.fetchAndExtract(baseUrl + "/missing"))
                .isInstanceOf(PermanentFetchException.class);
    }

    @Test
    void fetchAndExtract_throwsPermanentForPaywalledPageWithBarelyAnyContent() {
        assertThatThrownBy(() -> extractor.fetchAndExtract(baseUrl + "/paywall"))
                .isInstanceOf(PermanentFetchException.class);
    }

    @Test
    void fetchAndExtract_throwsPermanentForNonHtmlContentType() {
        assertThatThrownBy(() -> extractor.fetchAndExtract(baseUrl + "/wrong-type"))
                .isInstanceOf(PermanentFetchException.class);
    }

    @Test
    void fetchAndExtract_throwsRetryableForServerErrorEvenWithAShortHtmlErrorPage() {
        assertThatThrownBy(() -> extractor.fetchAndExtract(baseUrl + "/server-error"))
                .isInstanceOf(RetryableFetchException.class);
    }
}
