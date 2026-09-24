package com.compendium.worker;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

// Fetches a URL and runs Mozilla's Readability algorithm (via the Readability4J
// port, ADR-0004) over it. jsoup is already on the classpath transitively via
// readability4j, and its Connection API fetches straight into the Document
// type Readability4J's constructor wants — no separate HTTP client needed.
public class ArticleExtractor {

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BODY_SIZE_BYTES = 5 * 1024 * 1024;

    public ExtractedArticle fetchAndExtract(String url) throws PermanentFetchException, RetryableFetchException {
        Connection.Response response = connect(url);
        Document document = parse(response, url);
        Article article = new Readability4J(url, document).parse();

        classifyOutcome(response.statusCode(), response.contentType(), article.getTextContent());

        return new ExtractedArticle(article.getTitle(), article.getExcerpt(), article.getTextContent());
    }

    private Connection.Response connect(String url) throws RetryableFetchException {
        try {
            // ignoreHttpErrors/ignoreContentType: without these, jsoup
            // throws on any 4xx/5xx or non-HTML content type before
            // classifyOutcome ever gets a chance to look at either and
            // decide what they mean.
            return Jsoup.connect(url)
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
    private void classifyOutcome(int statusCode, String contentType, String textContent)
            throws PermanentFetchException, RetryableFetchException {
                if (statusCode >= 400 && statusCode < 500) {
                    throw new PermanentFetchException("Client error status code: " + statusCode);
                }
                if (statusCode >= 500 && statusCode < 600) {
                    throw new RetryableFetchException("Server error: " + statusCode);
                }
                if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
                    throw new PermanentFetchException("Non-HTML content type: " + contentType);
                }
                if (textContent == null || textContent.length() < 100) { // arbitrary threshold for "near-empty"
                    throw new PermanentFetchException("Extracted content is too short or empty");
                }
            }
}
