package com.compendium.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
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

    // Held as a field so refreshToken() can call setPassword() without going
    // back through the application context.
    private HikariDataSource pool;

    @Bean
    public DataSource dataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + dbEndpoint + ":3306/compendium?useSSL=true&requireSSL=true");
        cfg.setUsername(iamUsername);
        cfg.setPassword(generateToken());
        // Conservative pool size for t3.micro (1 vCPU, 1 GiB RAM).
        cfg.setMaximumPoolSize(5);
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

    private String generateToken() {
        return RdsUtilities.builder()
                .region(Region.of(region))
                .build()
                .generateAuthenticationToken(b -> b
                        .hostname(dbEndpoint)
                        .port(3306)
                        .username(iamUsername));
    }
}
