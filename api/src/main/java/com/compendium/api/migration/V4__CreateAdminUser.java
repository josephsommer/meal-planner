package com.compendium.api.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;

/**
 * Seeds the initial admin user.
 *
 * This is a Spring-managed Flyway Java migration rather than plain SQL:
 * Spring Boot's FlywayAutoConfiguration collects any JavaMigration bean
 * found in the application context and hands it to Flyway alongside the
 * SQL migrations it finds by scanning db/migration. Being a bean means this
 * class can take admin-username/admin-password via @Value like any other
 * component, so the raw secret is hashed here at migration time and only
 * the hash is ever written to the database — a SQL-only migration would
 * instead require a bcrypt hash to be pre-computed and supplied out of band.
 * It also means this can take the same PasswordEncoder bean SecurityConfig
 * defines, rather than constructing its own — if that bean's strength or
 * algorithm ever changes, this migration doesn't silently drift out of sync
 * with it (BCrypt hashes are self-describing, so a mismatch wouldn't error,
 * it would just quietly stop matching what the rest of the app expects).
 *
 * Deliberately NOT in the db.migration package: that's the package Flyway's
 * own classpath scan of db/migration maps to, and this class would be
 * picked up a second time (and fail on the duplicate version) if it lived
 * there. Living outside it means it's found exactly once, via Spring's
 * component scan.
 */
@Component
public class V4__CreateAdminUser extends BaseJavaMigration {

    private final String adminUsername;
    private final String adminPassword;
    private final PasswordEncoder passwordEncoder;

    public V4__CreateAdminUser(
            @Value("${admin-username}") String adminUsername,
            @Value("${admin-password}") String adminPassword,
            PasswordEncoder passwordEncoder) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void migrate(Context context) throws Exception {
        String passwordHash = passwordEncoder.encode(adminPassword);
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
            statement.setString(1, adminUsername);
            statement.setString(2, passwordHash);
            statement.executeUpdate();
        }
    }
}
