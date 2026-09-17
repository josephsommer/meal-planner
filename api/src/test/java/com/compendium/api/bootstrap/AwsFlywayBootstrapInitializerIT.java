package com.compendium.api.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Exercises AwsFlywayBootstrapInitializer end to end against a real MySQL
// container standing in for the master connection: property reading, Flyway
// wiring, and the hand-built V4__CreateAdminUser all have to work together
// for the admin user to actually land in the database.
//
// spring.flyway.locations is pointed at classpath:db/migration only — V2 in
// db/aws-migration uses AWSAuthenticationPlugin, which doesn't exist outside
// real RDS. That's an existing gap (nothing tests V2 today either), not one
// introduced here.
@Testcontainers
class AwsFlywayBootstrapInitializerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    void initialize_migratesAndSeedsTheAdminUser() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("aws");
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.flyway.url", mysql.getJdbcUrl(),
                    "spring.flyway.user", mysql.getUsername(),
                    "spring.flyway.password", mysql.getPassword(),
                    "spring.flyway.locations", "classpath:db/migration",
                    "admin-username", "test-admin",
                    "admin-password", "test-password")));

            new AwsFlywayBootstrapInitializer().initialize(ctx);
        }

        try (Connection conn = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT password_hash FROM users WHERE username = 'test-admin'")) {
            assertThat(rs.next()).as("seeded admin row").isTrue();
            assertThat(new BCryptPasswordEncoder().matches("test-password", rs.getString("password_hash"))).isTrue();
        }
    }
}
