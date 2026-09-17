package com.compendium.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

// Regression test for a prod bootstrapping deadlock: Spring Boot's
// FlywayAutoConfiguration.flyway() calls ObjectProvider<DataSource>.getIfUnique()
// unconditionally while building the flyway bean, as a fallback candidate it
// ends up never actually using once spring.flyway.url is set (see
// FlywayConfiguration#getMigrationDataSource). That call is a real,
// non-deferrable lookup - it eagerly constructs whatever DataSource bean
// exists in the context, regardless of whether Flyway ends up using it.
// (An earlier version of this fix tried @Lazy on the bean instead; it does
// not work, because @Lazy only defers construction for proxy-based
// injection points, not a direct ObjectProvider.getIfUnique() call like
// Flyway's - this test caught that.)
//
// Without HikariConfig#setInitializationFailTimeout(-1),
// dataSource()'s call to `new HikariDataSource(cfg)` validates a real
// connection at construction time and throws if it fails - which would
// happen here before Flyway has run a single migration, including the one
// that creates the DB user this DataSource authenticates as. This test
// reproduces exactly Flyway's resolution path (ObjectProvider.getIfUnique())
// against a deliberately unreachable endpoint: bean construction must not
// throw, even though the pool could never actually connect.
class RdsIamDataSourceConfigDeferredConnectionTest {

    @Test
    void dataSourceBean_constructsWithoutThrowingEvenWhenUnreachable() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("aws");
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "db.endpoint", "unreachable.invalid.example",
                    "db.iam-username", "compendium_app",
                    "db.region", "us-east-1")));
            ctx.register(PropertySourcesPlaceholderConfigurer.class);
            ctx.registerBean(AwsCredentialsProvider.class,
                    () -> StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")));
            ctx.register(RdsIamDataSourceConfig.class);

            assertThatCode(ctx::refresh).doesNotThrowAnyException();

            // Mirrors exactly what FlywayAutoConfiguration does: a direct,
            // unqualified ObjectProvider lookup, not injection.
            ObjectProvider<DataSource> dataSource = ctx.getBeanProvider(DataSource.class);
            assertThatCode(dataSource::getIfUnique).doesNotThrowAnyException();
        }
    }
}
