package com.compendium.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

// Active only on the "aws" profile (set by SPRING_PROFILES_ACTIVE in EB).
// Provides a HikariCP DataSource that authenticates as the compendium_app MySQL
// user using a 15-minute IAM auth token rather than a static password.
// The token is refreshed every 10 minutes so the pool never presents an expired
// credential when opening a new connection.
@Configuration
@Profile("aws")
public class RdsIamDataSourceConfig {

    @Value("${db.endpoint}")
    private String dbEndpoint;

    @Value("${db.iam-username}")
    private String iamUsername;

    @Value("${db.region:us-east-1}")
    private String region;

    // Unlike an RdsClient built via RdsClient.builder(), a standalone
    // RdsUtilities instance doesn't fall back to the default credential
    // chain on its own — it throws "CredentialProvider should be provided"
    // unless one is set explicitly on the builder. Reusing the
    // AwsCredentialsProvider bean spring-cloud-aws-autoconfigure already
    // exposes (backed by the default chain, i.e. the EC2 instance role in
    // prod) keeps this in sync with how every other AWS client in this app
    // resolves credentials, rather than constructing a second one by hand.
    private final AwsCredentialsProvider credentialsProvider;

    // Held as a field so refreshToken() can call setPassword() without going
    // back through the application context.
    private HikariDataSource pool;

    public RdsIamDataSourceConfig(AwsCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    // initializationFailTimeout(-1) is load-bearing, not an optimization.
    // Spring Boot's own FlywayAutoConfiguration resolves an
    // ObjectProvider<DataSource> unconditionally inside its flyway() bean
    // method, via getIfUnique() — a real, non-deferrable lookup that
    // eagerly constructs whatever DataSource bean exists, even though
    // spring.flyway.url (set in application-aws.yml) means Flyway never
    // actually ends up using it. (@Lazy on this bean does NOT help here:
    // it only defers construction for proxy-based injection points, not a
    // direct ObjectProvider.getIfUnique() call like Flyway's — see
    // RdsIamDataSourceConfigLazyTest.) That means this pool would
    // otherwise validate a connection, as compendium_app, before Flyway
    // has run a single migration — a hard bootstrapping deadlock in prod,
    // since compendium_app only exists once V2__create_app_user.sql has
    // actually run. Setting initializationFailTimeout to a negative value
    // makes HikariDataSource's constructor return immediately regardless
    // of whether a connection can be established yet, deferring the real
    // first connection attempt to whenever something actually calls
    // getConnection() — by which point JPA's entityManagerFactory (which
    // already depends on flyway completing first) will have already
    // triggered Flyway's migrations via its own, separate connection.
    @Bean
    public DataSource dataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + dbEndpoint + ":3306/compendium?useSSL=true&requireSSL=true");
        cfg.setUsername(iamUsername);
        cfg.setPassword(generateToken());
        // Conservative pool size for t3.micro (1 vCPU, 1 GiB RAM).
        cfg.setMaximumPoolSize(5);
        cfg.setInitializationFailTimeout(-1);
        pool = new HikariDataSource(cfg);
        return pool;
    }

    // Runs every 10 minutes. Tokens expire at 15 minutes; refreshing at 10
    // ensures the pool always has a valid credential for new connections.
    // Existing connections in the pool are unaffected — MySQL validates the
    // token only at handshake time, not on every query.
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void refreshToken() {
        if (pool != null) {
            pool.setPassword(generateToken());
        }
    }

    // Package-private rather than private so RdsIamDataSourceConfigTest can
    // exercise it directly with a stand-in AwsCredentialsProvider, without
    // needing a real DataSource/HikariPool or a live AWS environment —
    // generating the token is a local SigV4 signing operation, not a network
    // call.
    String generateToken() {
        return RdsUtilities.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build()
                .generateAuthenticationToken(b -> b
                        .hostname(dbEndpoint)
                        .port(3306)
                        .username(iamUsername));
    }
}
