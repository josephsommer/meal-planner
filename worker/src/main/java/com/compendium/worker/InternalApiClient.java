package com.compendium.worker;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;

// Reports an extraction result back to the API's internal callback
// endpoint. A failure here (network error, or the API itself returning
// 5xx) always propagates as an IOException — whether the extraction
// succeeded or was a permanent failure being reported, nothing was
// persisted, so the caller should treat it as worth another SQS delivery
// attempt rather than silently discarding the result (see SqsConsumerHandler).
public class InternalApiClient {

    private final HttpClient httpClient = buildHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String internalSecret;

    public InternalApiClient(String baseUrl, String internalSecret) {
        this.baseUrl = baseUrl;
        this.internalSecret = internalSecret;
    }

    // The API terminates TLS with a self-signed cert (free-tier choice over
    // ACM+CloudFront — see .ebextensions/https-instance.config), so the
    // JDK's default trust store has no reason to trust it. This pins trust
    // to exactly that one bundled certificate (api-server.crt) instead of
    // either failing every call or, worse, trusting any server — the
    // latter would hand X-Internal-Secret to whoever answers on 443.
    // The cert also has no SAN and a CN that doesn't match this host, so
    // endpoint identification is disabled too: safe specifically because
    // pinning the exact certificate bytes already proves we're talking to
    // the one server holding the matching private key, which is the
    // property hostname verification exists to establish in the first place.
    private static HttpClient buildHttpClient() {
        try {
            Certificate pinnedCert;
            try (InputStream certStream = InternalApiClient.class.getResourceAsStream("/api-server.crt")) {
                pinnedCert = CertificateFactory.getInstance("X.509").generateCertificate(certStream);
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("api-server", pinnedCert);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);

            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize pinned-certificate HTTP client", e);
        }
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
