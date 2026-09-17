package com.compendium.api.bootstrap;

import com.compendium.api.CompendiumApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// Proves the bootstrap ordering guarantee through the real main()-style
// wiring, not a hand-simulated bean lookup. Both the Flyway master connection
// and the app's IAM connection are pointed at unreachable hosts; the app must
// fail on the FLYWAY host, never the app-datasource host — evidence that
// migration is attempted (and fails) before RdsIamDataSourceConfig's bean is
// ever constructed. If AwsFlywayBootstrapInitializer regressed and stopped
// running before FlywayAutoConfiguration/RdsIamDataSourceConfig, this would
// instead fail on the app-datasource host (or not fail at the expected point
// at all).
//
// Command-line-style args (via run(String...), not properties()/defaultProperties())
// are required here: they're the only override with higher precedence than
// application-aws.yml, which is what lets this override spring.flyway.url
// and admin-password without going through their real placeholders
// (${SPRING_DATASOURCE_URL}, ${ADMIN_PASSWORD}) or the real SSM import.
class AwsFlywayBootstrapOrderingIT {

    @Test
    void startup_failsOnTheFlywayHost_beforeTouchingTheAppDataSource() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(CompendiumApiApplication.class)
                .profiles("aws")
                .initializers(new AwsFlywayBootstrapInitializer());

        Throwable thrown = catchThrowable(() -> app.run(
                "--spring.cloud.aws.parameterstore.enabled=false",
                "--spring.flyway.url=jdbc:mysql://flyway-master.invalid:3306/compendium",
                "--spring.flyway.user=master",
                "--spring.flyway.password=unused",
                "--admin-username=test-admin",
                "--admin-password=unused",
                "--db.endpoint=app-datasource.invalid",
                "--db.iam-username=compendium_app"));

        assertThat(thrown).as("startup should fail").isNotNull();
        String trace = stackTraceOf(thrown);
        assertThat(trace).as("failure should reference the Flyway host").contains("flyway-master.invalid");
        assertThat(trace).as("failure should never reach the app datasource host")
                .doesNotContain("app-datasource.invalid");
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
