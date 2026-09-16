package com.compendium.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;

// Regression test for a startup failure: RdsUtilities.builder(), unlike
// RdsClient.builder(), does not fall back to the default credential chain
// on its own, so generateToken() threw "CredentialProvider should be
// provided either in GenerateAuthenticationTokenRequest object or
// RdsUtilities object" until credentialsProvider(...) was set explicitly.
// Generating the token is a local SigV4 signing operation (no network
// call), so a stand-in StaticCredentialsProvider is enough here — no real
// AWS environment needed.
class RdsIamDataSourceConfigTest {

    @Test
    void generateToken_returnsATokenWhenACredentialsProviderIsConfigured() {
        RdsIamDataSourceConfig config = new RdsIamDataSourceConfig(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key")));
        ReflectionTestUtils.setField(config, "dbEndpoint", "db.example.com");
        ReflectionTestUtils.setField(config, "iamUsername", "compendium_app");
        ReflectionTestUtils.setField(config, "region", "us-east-1");

        String token = config.generateToken();

        assertThat(token).isNotBlank();
    }
}
