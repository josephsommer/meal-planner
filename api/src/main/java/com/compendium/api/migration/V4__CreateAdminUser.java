package com.compendium.api.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    public V4__CreateAdminUser(
            @Value("${admin-username}") String adminUsername,
            @Value("${admin-password}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void migrate(Context context) throws Exception {
        String passwordHash = new BCryptPasswordEncoder().encode(adminPassword);
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
            statement.setString(1, adminUsername);
            statement.setString(2, passwordHash);
            statement.executeUpdate();
        }
    }
}
