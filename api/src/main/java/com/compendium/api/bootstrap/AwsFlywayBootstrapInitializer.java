package com.compendium.api.bootstrap;

import com.compendium.api.migration.V4__CreateAdminUser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Runs Flyway manually under the aws profile, in place of Spring Boot's own
// FlywayAutoConfiguration (excluded via spring.autoconfigure.exclude in
// application-aws.yml). That autoconfiguration unconditionally probes the
// primary DataSource bean while building itself — even though spring.flyway.url
// means Flyway never actually uses that bean — which used to eagerly construct
// RdsIamDataSourceConfig's pool (as compendium_app) before this migration had
// run V2__create_app_user.sql, the migration that creates that user. See
// RdsIamDataSourceConfig for the prod outage that caused and the previous
// Hikari-level workaround.
//
// Registered via SpringApplication.addInitializers() in main(), this runs
// during ApplicationContextInitializer processing — after the Environment
// (including SSM-backed properties from spring.config.import) is resolved,
// but strictly before any @Configuration class is scanned or any bean
// definition registered. There is no RdsIamDataSourceConfig bean for this to
// race against yet, so the old ordering problem is structurally impossible
// rather than merely deferred.
//
// V4__CreateAdminUser is normally a Spring bean so it can reuse SecurityConfig's
// PasswordEncoder bean. That bean doesn't exist at this point in startup, so
// it's hand-instantiated here with a fresh BCryptPasswordEncoder instead —
// SecurityConfig.passwordEncoder() is a plain `new BCryptPasswordEncoder()`
// with no configurable state, so there's nothing for the two call sites to
// drift on.
public class AwsFlywayBootstrapInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("aws"))) {
            return;
        }

        String url = env.getRequiredProperty("spring.flyway.url");
        String user = env.getRequiredProperty("spring.flyway.user");
        String password = env.getRequiredProperty("spring.flyway.password");
        String adminUsername = env.getRequiredProperty("admin-username");
        String adminPassword = env.getRequiredProperty("admin-password");
        String[] locations = Binder.get(env)
                .bind("spring.flyway.locations", String[].class)
                .orElseThrow(() -> new IllegalStateException(
                        "spring.flyway.locations must be set under the aws profile"));

        // Not a Spring-managed bean, so nothing else will close this — the
        // try-with-resources releases it as soon as migration finishes rather
        // than holding a connection slot on a t3.micro for the app's lifetime.
        // Left at Hikari's default (fail-fast) initializationFailTimeout,
        // unlike RdsIamDataSourceConfig's pool: if the master connection is
        // unreachable, migration should fail loudly here rather than defer.
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(2);
        try (HikariDataSource migrationDataSource = new HikariDataSource(cfg)) {
            Flyway flyway = Flyway.configure(getClass().getClassLoader())
                    .dataSource(migrationDataSource)
                    .locations(locations)
                    .javaMigrations(new V4__CreateAdminUser(adminUsername, adminPassword, new BCryptPasswordEncoder()))
                    .load();
            flyway.migrate();
        }
    }
}
