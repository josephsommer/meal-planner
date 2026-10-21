package com.compendium.worker;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

// Confirms the pinned-certificate HTTP client is actually wired the way
// InternalApiClient's buildHttpClient() comment claims: a non-default
// SSLContext (proving the pinned trust store is in effect, not a silent
// fallback to the JDK's default CA trust) and endpoint identification
// disabled (required because the bundled cert's CN doesn't match the API's
// host and it carries no SAN at all). A live TLS handshake against the real
// pinned cert isn't tested here — that would need the matching private key,
// which never belongs in this repo.
class InternalApiClientTest {

    @Test
    void usesNonDefaultSslContext() throws Exception {
        InternalApiClient client = new InternalApiClient("https://example.invalid", "secret");
        HttpClient httpClient = extractHttpClient(client);

        assertThat(httpClient.sslContext()).isNotSameAs(SSLContext.getDefault());
    }

    @Test
    void disablesEndpointIdentification() throws Exception {
        InternalApiClient client = new InternalApiClient("https://example.invalid", "secret");
        HttpClient httpClient = extractHttpClient(client);

        assertThat(httpClient.sslParameters().getEndpointIdentificationAlgorithm()).isNull();
    }

    @Test
    void bundledCertMatchesTheOnePinnedForProd() throws Exception {
        try (InputStream certStream = getClass().getResourceAsStream("/api-server.crt")) {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(certStream);

            assertThat(cert.getSubjectX500Principal().getName())
                    .contains("CN=compendium-web-817685573568.s3.us-east-1.amazonaws.com");
        }
    }

    private static HttpClient extractHttpClient(InternalApiClient client) throws Exception {
        var field = InternalApiClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(client);
    }
}
