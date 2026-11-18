package com.compendium.worker;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

// Fetches a URL and runs Mozilla's Readability algorithm (via the Readability4J
// port, ADR-0004) over it. jsoup is already on the classpath transitively via
// readability4j, and its Connection API fetches straight into the Document
// type Readability4J's constructor wants — no separate HTTP client needed.
public class ArticleExtractor {

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BODY_SIZE_BYTES = 5 * 1024 * 1024;

    // jsoup's default User-Agent claims to be Chrome but sends no Accept or
    // Accept-Language, a mismatch bot defenses flag. These mirror what a
    // browser navigating to a page sends.
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";

    // Response headers that help tell a site refusing us (WAF/bot challenge)
    // apart from an ordinary error. Values come from a third party and end
    // up in last_error, so they are truncated.
    private static final List<String> DIAGNOSTIC_HEADERS = List.of("server", "cf-mitigated", "content-type", "retry-after");
    private static final int MAX_DIAGNOSTIC_VALUE_LENGTH = 60;

    // jsoup sends this unless a User-Agent is set explicitly.
    public String userAgent() {
        return HttpConnection.DEFAULT_UA;
    }

    public ExtractedArticle fetchAndExtract(String url) throws PermanentFetchException, RetryableFetchException {
        Connection.Response response = connect(url);
        Document document = parse(response, url);
        Article article = new Readability4J(url, document).parse();

        classifyOutcome(response, article.getTextContent());

        return new ExtractedArticle(article.getTitle(), article.getExcerpt(), article.getTextContent());
    }

    private Connection.Response connect(String url) throws RetryableFetchException {
        try {
            // ignoreHttpErrors/ignoreContentType: without these, jsoup
            // throws on any 4xx/5xx or non-HTML content type before
            // classifyOutcome ever gets a chance to look at either and
            // decide what they mean.
            return Jsoup.connect(url)
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .timeout(TIMEOUT_MILLIS)
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute();
        } catch (IOException e) {
            // A connection-level failure (timeout, DNS, connection refused)
            // rather than an HTTP response at all — always worth another
            // attempt, independent of the response-based classification below.
            throw new RetryableFetchException("Failed to connect to " + url, e);
        }
    }

    private Document parse(Connection.Response response, String url) throws RetryableFetchException {
        try {
            return response.parse();
        } catch (IOException e) {
            // The connection succeeded but reading/parsing the body failed
            // (e.g. truncated response) — a transient condition, not a
            // property of the page itself, so worth another attempt.
            throw new RetryableFetchException("Failed to read response body from " + url, e);
        }
    }

    // " (server=cloudflare, cf-mitigated=challenge)" for whichever diagnostic
    // headers are present, or "" if none are.
    private String diagnostics(Connection.Response response) {
        String details = DIAGNOSTIC_HEADERS.stream()
                .filter(response::hasHeader)
                .map(name -> name + "=" + truncate(response.header(name)))
                .collect(Collectors.joining(", "));
        return details.isEmpty() ? "" : " (" + details + ")";
    }

    private static String truncate(String value) {
        return value.length() <= MAX_DIAGNOSTIC_VALUE_LENGTH ? value : value.substring(0, MAX_DIAGNOSTIC_VALUE_LENGTH);
    }

    // classification for a fetched-and-parsed article.
    //
    // Given:
    //   - statusCode: the HTTP response status from fetching the article URL
    //   - contentType: the response's Content-Type header (may be null)
    //   - textContent: the plain text Readability4J already extracted
    //
    // Throw PermanentFetchException for a failure that won't succeed on
    // retry — per the async-extraction spec (plans/tranquil-rolling-ladybug.md,
    // section 5): a 404/410 status, a non-HTML content type, or Readability
    // returning empty/near-empty content (e.g. a paywall or SPA shell with
    // nothing real to extract — you decide what "near-empty" means here).
    //
    // Throw RetryableFetchException for a failure worth another SQS delivery
    // attempt — per the spec: a 5xx status from the target site.
    //
    // Return normally (no exception) for a genuine success. Note this runs
    // AFTER the page was already fetched and parsed, so a
    // PermanentFetchException here still means the callback reports failure
    // for a real reason, not wasted work.
    private void classifyOutcome(Connection.Response response, String textContent)
            throws PermanentFetchException, RetryableFetchException {
                int statusCode = response.statusCode();
                String contentType = response.contentType();
                if (statusCode >= 400 && statusCode < 500) {
                    throw new PermanentFetchException("Client error status code: " + statusCode + diagnostics(response));
                }
                if (statusCode >= 500 && statusCode < 600) {
                    throw new RetryableFetchException("Server error: " + statusCode + diagnostics(response));
                }
                if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
                    throw new PermanentFetchException("Non-HTML content type: " + contentType);
                }
                if (textContent == null || textContent.length() < 100) { // arbitrary threshold for "near-empty"
                    throw new PermanentFetchException("Extracted content is too short or empty");
                }
            }
}
