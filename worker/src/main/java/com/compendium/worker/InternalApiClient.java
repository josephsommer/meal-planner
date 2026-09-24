package com.compendium.worker;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Reports an extraction result back to the API's internal callback
// endpoint. A failure here (network error, or the API itself returning
// 5xx) always propagates as an IOException — whether the extraction
// succeeded or was a permanent failure being reported, nothing was
// persisted, so the caller should treat it as worth another SQS delivery
// attempt rather than silently discarding the result (see SqsConsumerHandler).
public class InternalApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String internalSecret;

    public InternalApiClient(String baseUrl, String internalSecret) {
        this.baseUrl = baseUrl;
        this.internalSecret = internalSecret;
    }

    public void reportSuccess(Long articleId, ExtractedArticle article) throws IOException, InterruptedException {
        send(articleId, new FetchResultRequest(true, article.title(), article.excerpt(), article.content(), null));
    }

    public void reportFailure(Long articleId, String errorMessage) throws IOException, InterruptedException {
        send(articleId, new FetchResultRequest(false, null, null, null, errorMessage));
    }

    private void send(Long articleId, FetchResultRequest body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/internal/articles/" + articleId + "/fetch-result"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Internal-Secret", internalSecret)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Internal callback for article " + articleId
                    + " returned " + response.statusCode());
        }
    }
}
